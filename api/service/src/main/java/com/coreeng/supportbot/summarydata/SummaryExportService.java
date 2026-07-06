package com.coreeng.supportbot.summarydata;

import com.coreeng.supportbot.asyncjob.AsyncJobRepository;
import com.coreeng.supportbot.config.SlackChannelRegistry;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
import org.springframework.stereotype.Service;

/**
 * Orchestrates the async Knowledge Gap thread export: a single shared, disk-backed zip built on a
 * background thread so the triggering HTTP request never blocks on the (often 10+ minute) Slack
 * fetch. See {@code SummaryExportController} for the REST surface.
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

    // Package-private (not private) so tests can seed a completed export directly — there's no
    // production path to populate one without running the real (slow, Slack-calling) export.
    final AtomicReference<@Nullable CompletedExport> currentExport = new AtomicReference<>();

    // Package-private (not private) so tests can redirect writes to an isolated @TempDir instead of
    // this real shared path. No operational need has ever come up to override it otherwise.
    Path exportDirectory = Path.of("/tmp/summary-export");

    /**
     * @param running whether an export is currently in progress
     * @param startedAt when the current (or most recent) run started, null if never run
     * @param error error message from the most recent run, null if it succeeded or none has run
     */
    public record ExportStatus(
            boolean running,
            @Nullable Instant startedAt,
            @Nullable String error) {}

    /**
     * Pointer to the single most recently finished export artifact, kept in memory since the file
     * itself lives on local ephemeral disk. It's kept around — no time-based expiry — until a new
     * export replaces it (see {@link #start}).
     */
    public record CompletedExport(Path filePath, String displayFilename, int threadCount, Instant completedAt) {}

    /**
     * Discards any export job left running by a pod restart, rather than resuming it.
     *
     * <p>Unlike {@code AnalysisService}, which checkpoints each analyzed thread to a DB repository
     * as it goes, this job's only output is a single file with no incremental progress to resume
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

        // Discard whatever finished export is already sitting there, on the caller's thread and
        // before dispatching the slow work — a new export replaces the old one from the moment
        // it's requested, not from the moment it finishes.
        discardCurrentExport();

        try {
            log.info("Started new async job: id={}, days={}", ASYNC_ID, days);
            applicationContext.getBean(SummaryExportService.class).runAsyncExport(days);
            return true;
        } catch (TaskRejectedException e) {
            log.error("Executor rejected export job, cleaning up DB record", e);
            asyncJobRepository.deleteJob(ASYNC_ID);
            return false;
        }
    }

    private void discardCurrentExport() {
        CompletedExport previous = currentExport.getAndSet(null);
        if (previous != null) {
            deleteQuietly(previous.filePath());
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to delete export file {}", path, e);
        }
    }

    /**
     * Runs the export job asynchronously on the {@code summaryExportTaskExecutor}: fetches every
     * monitored channel's checked-off threads, writes them to a zip on local disk, and — only on
     * success — publishes it via {@link #currentExport}.
     */
    @Async("summaryExportTaskExecutor")
    public void runAsyncExport(int days) {
        Instant startedAt = Instant.now();
        currentStatus.set(new ExportStatus(true, startedAt, null));

        Path tempFile = null;
        try {
            List<String> channelIds = channelRegistry.monitoredChannelIds();
            log.info("Exporting summary data for last {} days from channels {}", days, channelIds);

            // A failure for one channel (e.g. the bot is not a member, or a Slack API error) must
            // not fail the whole export, so each channel is fetched independently and a failing
            // one is skipped.
            List<ThreadService.ThreadData> threads = new ArrayList<>();
            for (String channelId : channelIds) {
                try {
                    threads.addAll(threadService.getThreadsWithCheckMarkAsText(channelId, days));
                } catch (Exception e) {
                    log.warn("Failed to fetch threads for channel {}; skipping it in the export", channelId, e);
                }
            }
            log.info("Found {} threads to export", threads.size());

            Files.createDirectories(exportDirectory);
            tempFile = Files.createTempFile(exportDirectory, "summary-export-", ".zip.tmp");
            writeZip(tempFile, threads);

            Instant completedAt = Instant.now();
            String displayFilename = displayFilename(days, completedAt);
            Path finalPath = exportDirectory.resolve(displayFilename);
            Files.move(tempFile, finalPath, StandardCopyOption.REPLACE_EXISTING);
            tempFile = null; // moved successfully — no longer ours to clean up

            currentExport.set(new CompletedExport(finalPath, displayFilename, threads.size(), completedAt));
            currentStatus.set(new ExportStatus(false, startedAt, null));
            log.info("Successfully exported {} threads to {}", threads.size(), finalPath);
        } catch (Exception e) {
            log.error("Export job {} failed", ASYNC_ID, e);
            currentStatus.set(new ExportStatus(false, startedAt, e.toString()));
        } finally {
            if (tempFile != null) {
                deleteQuietly(tempFile);
            }
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
    private static void writeZip(Path path, List<ThreadService.ThreadData> threads) throws IOException {
        Set<String> usedNames = new HashSet<>();
        try (OutputStream fileOut = Files.newOutputStream(path);
                ZipOutputStream zip = new ZipOutputStream(fileOut)) {
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
     * The currently servable export, if any. There's no time-based expiry — it stays servable
     * until a new export replaces it via {@link #start}.
     */
    public Optional<CompletedExport> currentServableExport() {
        return Optional.ofNullable(currentExport.get());
    }
}
