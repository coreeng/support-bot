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
 * thread so the triggering HTTP request never blocks on the (often 10+ minute) Slack fetch. See
 * {@code SummaryExportController} for the REST surface.
 *
 * <p>The finished zip is held in memory. It's consumed
 * (served once, then cleared) on first download, and swept away within about 15 minutes of
 * {@link #RETENTION_DURATION} elapsing even if nobody claimed it — see {@link #expireStaleExport}
 * for the exact bound — so nothing lingers indefinitely just because no one checked back.
 *
 * <p>Concurrency is controlled the same way as {@code AnalysisService} — via a unique constraint
 * on the {@code async_job} table (a distinct {@code "summary-export"} row alongside the existing
 * {@code "analysis"} one) — so only one export can be running at a time.
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

    /**
     * Test-only seam: seeds a completed export directly, since there's no production path to
     * populate one without running the real (slow, Slack-calling) export.
     */
    void seedCompletedExportForTest(CompletedExport export) {
        currentExport.set(export);
    }

    /**
     * Test-only seam: reads the raw held reference, so a test can verify a clearing path actually
     * cleared it rather than just inferring that from later behavior.
     */
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

    /**
     * The single most recently finished export artifact, held in memory only. Cleared the instant
     * it's served (see {@link #consumeCurrentExport}), replaced the instant a new export starts (see
     * {@link #start}), or swept away once {@code expiresAt} passes, whichever happens first.
     */
    // ErrorProne flags any array record component unconditionally, regardless of whether equals and
    // hashCode are actually overridden below — they are, content-aware via Arrays.equals/hashCode,
    // so the default reference-only comparison this check warns about doesn't apply here.
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

    /**
     * Discards any export job left running by a pod restart, rather than resuming it.
     *
     * <p>Unlike {@code AnalysisService}, which checkpoints each analyzed thread to a DB repository
     * as it goes, this job's only output is a single artifact with no incremental progress to resume
     * from — re-running it would just mean silently kicking off a fresh 10+ minute Slack fetch on
     * every startup that nobody asked for. Simpler and safer to let the next manual click start
     * fresh, consistent with this feature not needing to survive a restart.
     */
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

        // Discard whatever finished export is already sitting there — a new export replaces the old
        // one from the moment it's requested, not from the moment it finishes. Nothing to clean up
        // beyond dropping the reference: the old content is just heap memory now, not a file.
        currentExport.set(null);

        try {
            log.info("Started new async job: id={}, days={}", ASYNC_ID, days);
            // Fetched through the Spring-managed bean, not called as this.runAsyncExport(days) —
            // @Async only takes effect through the CGLIB proxy Spring wraps around the bean. A
            // direct "this" call would bypass that proxy and run synchronously on this thread,
            // blocking the request for the full 10+ minute Slack fetch this whole feature exists to
            // avoid. Same pattern as AnalysisService.
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

            // A failure for one channel (e.g. the bot is not a member, or a Slack API error) must
            // not fail the whole export, so each channel is fetched independently and a failing
            // one is skipped. Only logged, not surfaced to the caller — acceptable while there's
            // typically just one monitored channel. If multi-channel monitoring becomes common,
            // we'd need to track skipped channels and expose them (e.g. on ExportStatus) so a
            // "successful" export can't silently be missing a whole channel's threads.
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

    /**
     * Writes each thread as a separate {@code .txt} entry. Genuine duplicates within a single
     * channel (a thread_ts repeated by a reply_broadcast or a page boundary) are already deduped
     * at the source in {@link ThreadService}. The remaining collisions are cross-channel: two
     * genuinely different threads in different channels can share a thread_ts, so disambiguate the
     * name instead of dropping one and silently losing data.
     */
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

    /**
     * The currently servable export, if any — a read-only peek for {@code /status}, does not
     * consume it. Evicts (clears the pointer) and returns empty if it's past its retention window;
     * this is a defense-in-depth check alongside the scheduled sweep below, so a request landing
     * between sweeps never sees or serves stale data.
     */
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

    /**
     * Atomically takes and clears the current export for {@code /download} — the export is
     * single-consumption: whichever request gets here first claims it, and it's gone for everyone
     * else from that instant, even before this request finishes streaming it. Simpler than trying to
     * clear it only after a successful transfer, and it minimizes how long the data is available at
     * all, which is the point.
     */
    public Optional<CompletedExport> consumeCurrentExport() {
        CompletedExport export = currentExport.getAndSet(null);
        if (export == null || Instant.now().isAfter(export.expiresAt())) {
            return Optional.empty();
        }
        return Optional.of(export);
    }

    /**
     * Backstop sweep: clears the current export once it's past its retention window, regardless of
     * whether anyone has polled {@code /status} or {@code /download} since it finished. Needed
     * because eviction elsewhere here is lazy (only checked when something reads the reference) — if
     * nobody visits the page again after an export completes, lazy eviction alone would never fire,
     * defeating the point of a time-bounded retention guarantee.
     */
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
