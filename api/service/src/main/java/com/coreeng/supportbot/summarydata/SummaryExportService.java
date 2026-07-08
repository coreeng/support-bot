package com.coreeng.supportbot.summarydata;

import com.coreeng.supportbot.asyncjob.AsyncJobRepository;
import com.coreeng.supportbot.config.SlackChannelRegistry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the async Knowledge Gap thread export: a single shared zip built on a background
 * thread so the triggering HTTP request never blocks on the (often 10+ minute) Slack fetch. Held in
 * memory, single-consumption on download, swept after {@link #RETENTION_DURATION} if unclaimed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SummaryExportService {

    private static final String ASYNC_ID = "summary-export";

    private final AsyncJobRepository asyncJobRepository;
    private final ThreadService threadService;
    private final SlackChannelRegistry channelRegistry;
    private final ApplicationContext applicationContext;

    private static final ExportStatus IDLE_STATUS = new ExportStatus(false, null, null);
    private final AtomicReference<ExportStatus> currentStatus = new AtomicReference<>(IDLE_STATUS);

    private final AtomicReference<@Nullable CompletedExport> currentExport = new AtomicReference<>();

    private static final Duration RETENTION_DURATION = Duration.ofHours(8);

    // Test-only seam: there's no production path to populate/inspect a completed export directly.
    void seedCompletedExportForTest(CompletedExport export) {
        currentExport.set(export);
    }

    @Nullable CompletedExport currentExportForTest() {
        return currentExport.get();
    }

    /**
     * @param running whether an export is currently in progress
     * @param startedAt when the current (or most recent) run started, null if never run
     * @param error error message from the most recent run, null if it succeeded or none has run
     */
    public record ExportStatus(
            boolean running,
            @Nullable Instant startedAt,
            @Nullable String error) {
        public ExportStatus {
            if (running && error != null) {
                throw new IllegalArgumentException("A running export cannot already have a result error");
            }
            if (running && startedAt == null) {
                throw new IllegalArgumentException("A running export must have a startedAt");
            }
        }
    }

    // equals/hashCode overridden below (content-aware via Arrays.equals/hashCode), so the
    // reference-only comparison ErrorProne warns about here doesn't apply.
    @SuppressWarnings("ArrayRecordComponent")
    public record CompletedExport(
            byte[] content, String displayFilename, int threadCount, Instant completedAt, Instant expiresAt) {

        public CompletedExport {
            if (threadCount < 0) {
                throw new IllegalArgumentException("threadCount cannot be negative: " + threadCount);
            }
            if (!expiresAt.isAfter(completedAt)) {
                throw new IllegalArgumentException("expiresAt must be after completedAt");
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof CompletedExport other)) {
                return false;
            }
            return threadCount == other.threadCount
                    && Arrays.equals(content, other.content)
                    && displayFilename.equals(other.displayFilename)
                    && completedAt.equals(other.completedAt)
                    && expiresAt.equals(other.expiresAt);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hash(displayFilename, threadCount, completedAt, expiresAt) + Arrays.hashCode(content);
        }
    }

    // Discards rather than resumes a job left running by a restart — unlike AnalysisService, there's
    // no incremental progress to resume from, so re-running would just silently redo the whole fetch.
    @EventListener(ApplicationReadyEvent.class)
    public void resumeExportOnStartup() {
        AsyncJobRepository.AsyncJob existingJob = asyncJobRepository.findJob(ASYNC_ID);
        if (existingJob == null) {
            return;
        }

        log.warn(
                "Found stale export job {} from a previous run (started {}); discarding rather than resuming",
                ASYNC_ID,
                existingJob.startedAt());
        asyncJobRepository.deleteJob(ASYNC_ID);
    }

    /**
     * Attempts to start a new export for the given number of days. Returns true if started, false
     * if one is already running.
     */
    public boolean start(int days) {
        if (!asyncJobRepository.tryStartJob(ASYNC_ID, Integer.toString(days))) {
            log.warn("Cannot start async job {}: already running", ASYNC_ID);
            return false;
        }

        // A new export replaces the old one from the moment it's requested, not once it finishes.
        currentExport.set(null);

        try {
            log.info("Started new async job: id={}, days={}", ASYNC_ID, days);
            // Via the Spring-managed bean, not this.runAsyncExport(days) — @Async only applies
            // through the proxy; a direct call would run synchronously on this thread.
            applicationContext.getBean(SummaryExportService.class).runAsyncExport(days);
            return true;
        } catch (TaskRejectedException e) {
            log.error("Executor rejected export job, cleaning up DB record", e);
            asyncJobRepository.deleteJob(ASYNC_ID);
            return false;
        }
    }

    /**
     * Runs the export job asynchronously on the {@code summaryExportTaskExecutor}: fetches every
     * monitored channel's checked-off threads, zips them in memory, and — only on success —
     * publishes the result via {@link #currentExport}.
     */
    @Async("summaryExportTaskExecutor")
    public void runAsyncExport(int days) {
        Instant startedAt = Instant.now();
        currentStatus.set(new ExportStatus(true, startedAt, null));

        try {
            List<String> channelIds = channelRegistry.monitoredChannelIds();
            log.info("Exporting summary data for last {} days from channels {}", days, channelIds);

            // A failing channel shouldn't fail the whole export, so each is fetched independently
            // and skipped on error (only logged — fine while there's typically one channel; would
            // need surfacing to the caller if multi-channel monitoring becomes common).
            List<ThreadService.ThreadData> threads = new ArrayList<>();
            for (String channelId : channelIds) {
                try {
                    threads.addAll(threadService.getThreadsWithCheckMarkAsText(channelId, days));
                } catch (Exception e) {
                    log.warn("Failed to fetch threads for channel {}; skipping it in the export", channelId, e);
                }
            }
            log.info("Found {} threads to export", threads.size());

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            writeZip(buffer, threads);
            byte[] content = buffer.toByteArray();

            Instant completedAt = Instant.now();
            String displayFilename = displayFilename(days, completedAt);
            Instant expiresAt = completedAt.plus(RETENTION_DURATION);

            currentExport.set(new CompletedExport(content, displayFilename, threads.size(), completedAt, expiresAt));
            currentStatus.set(new ExportStatus(false, startedAt, null));
            log.info("Successfully exported {} threads ({} bytes)", threads.size(), content.length);
        } catch (Exception e) {
            log.error("Export job {} failed", ASYNC_ID, e);
            currentStatus.set(new ExportStatus(false, startedAt, e.toString()));
        } finally {
            asyncJobRepository.deleteJob(ASYNC_ID);
        }
    }

    // Cross-channel thread_ts collisions are disambiguated by suffix rather than dropped, since two
    // different threads in different channels can legitimately share a thread_ts.
    private static void writeZip(ByteArrayOutputStream out, List<ThreadService.ThreadData> threads) throws IOException {
        Set<String> usedNames = new HashSet<>();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (ThreadService.ThreadData thread : threads) {
                String fileName = thread.threadTs() + ".txt";
                for (int dup = 2; !usedNames.add(fileName); dup++) {
                    fileName = thread.threadTs() + "-" + dup + ".txt";
                }
                zip.putNextEntry(new ZipEntry(fileName));
                zip.write(thread.text().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            zip.finish();
        }
    }

    private static String displayFilename(int days, Instant completedAt) {
        LocalDate end = LocalDate.ofInstant(completedAt, ZoneOffset.UTC);
        LocalDate start = end.minusDays(days);
        return "downloaded-threads-" + start + "-to-" + end + ".zip";
    }

    public ExportStatus getStatus() {
        return currentStatus.get();
    }

    // Read-only peek for /status — doesn't consume. Also lazily evicts if past retention, as a
    // defense-in-depth check between scheduled sweeps.
    public Optional<CompletedExport> currentServableExport() {
        CompletedExport export = currentExport.get();
        if (export == null) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(export.expiresAt())) {
            currentExport.compareAndSet(export, null);
            return Optional.empty();
        }
        return Optional.of(export);
    }

    // Single-consumption: whichever /download request gets here first claims it, and it's gone
    // for everyone else from that instant.
    public Optional<CompletedExport> consumeCurrentExport() {
        CompletedExport export = currentExport.getAndSet(null);
        if (export == null || Instant.now().isAfter(export.expiresAt())) {
            return Optional.empty();
        }
        return Optional.of(export);
    }

    // Active backstop: without this, an export nobody ever polls again would never get evicted,
    // since eviction elsewhere is lazy (only checked when something reads the reference).
    @Scheduled(fixedRateString = "15m")
    public void expireStaleExport() {
        CompletedExport export = currentExport.get();
        if (export != null && Instant.now().isAfter(export.expiresAt())) {
            if (currentExport.compareAndSet(export, null)) {
                log.info("Discarded unclaimed export {} after its retention window elapsed", export.displayFilename());
            }
        }
    }
}
