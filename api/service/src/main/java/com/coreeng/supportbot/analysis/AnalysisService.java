package com.coreeng.supportbot.analysis;

import com.coreeng.supportbot.analysis.AnalysisRepository.AnalysisRecord;
import com.coreeng.supportbot.analysis.ThreadsAwaitingAnalysisRepository.ThreadToAnalyze;
import com.coreeng.supportbot.analysis.llm.LlmAnalysisService;
import com.coreeng.supportbot.asyncjob.AsyncJobRepository;
import com.coreeng.supportbot.config.AnalysisProps;
import com.coreeng.supportbot.config.SlackTicketsProps;
import com.google.common.collect.ImmutableList;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Service for orchestrating LLM-powered analysis of support tickets.
 *
 * <p>This service manages the lifecycle of analysis jobs, including:
 * <ul>
 *   <li>Starting new analysis jobs with database-level concurrency control via {@link AsyncJobRepository}</li>
 *   <li>Asynchronous processing of tickets using {@link LlmAnalysisService}</li>
 *   <li>Incremental persistence of analysis results to {@link AnalysisRepository}</li>
 *   <li>Automatic resume of interrupted jobs on application startup</li>
 *   <li>In-memory status tracking for progress monitoring via {@link AnalysisStatus}</li>
 * </ul>
 *
 * <p>Concurrency is controlled via a unique constraint on the {@code async_job} table,
 * ensuring only one analysis job can run at a time.
 *
 * <p>The analysis process runs asynchronously on a dedicated single-threaded executor
 * ({@code analysisTaskExecutor}) to avoid LLM rate limits and prevent double processing.
 *
 * <p>The service uses prompt versioning via {@code prompt_id} to avoid re-analyzing threads
 * when the prompt hasn't changed. The prompt ID is computed as a SHA-256 hash of the prompt
 * file content, so it auto-updates whenever the prompt changes.
 */
@Service
@ConditionalOnProperty(name = "analysis.prompt.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class AnalysisService {

    private static final String ASYNC_ID = "analysis";

    /**
     * Repository for managing async job state in the database.
     * Used to track running analysis jobs, prevent concurrent runs, and enable resume on startup.
     */
    private final AsyncJobRepository asyncJobRepository;

    private final ThreadsAwaitingAnalysisService threadsAwaitingAnalysisService;
    private final LlmAnalysisService llmAnalysisService;
    private final AnalysisRepository analysisRepository;
    private final AnalysisProps analysisProps;
    private final SlackTicketsProps slackTicketsProps;
    private final ApplicationContext applicationContext;

    private static final AnalysisStatus IDLE_STATUS = new AnalysisStatus(null, null, null, false, null);
    private final AtomicReference<AnalysisStatus> currentStatus = new AtomicReference<>(IDLE_STATUS);

    /**
     * Status record for tracking analysis job progress.
     *
     * @param jobId The async job ID (always "analysis" for this service)
     * @param exportedCount Total number of threads found that need analysis
     * @param analyzedCount Number of threads successfully analyzed so far
     * @param running Whether the analysis job is currently running
     * @param error Error message if the job failed, null otherwise
     */
    // TODO: add failedCount field to make skipped/failed threads visible in progress panel.
    //  This changes the /analysis/status API contract so needs an ADR update first.
    public record AnalysisStatus(
            @Nullable String jobId,
            @Nullable Integer exportedCount,
            @Nullable Integer analyzedCount,
            boolean running,
            @Nullable String error) {}

    /**
     * Resumes any pending analysis job on application startup.
     * This ensures that interrupted jobs (e.g., due to pod restart) are automatically resumed.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void resumeAnalysisOnStartup() {
        try {
            AsyncJobRepository.AsyncJob existingJob = asyncJobRepository.findJob(ASYNC_ID);
            if (existingJob == null) return;

            int days;
            try {
                days = Integer.parseInt(existingJob.data());
            } catch (NumberFormatException e) {
                log.error("Corrupt async job data '{}', deleting job", existingJob.data());
                asyncJobRepository.deleteJob(ASYNC_ID);
                return;
            }

            log.info("Found pending async job on startup: {}, resuming...", ASYNC_ID);
            applicationContext.getBean(AnalysisService.class).runAsyncAnalysis(days);
        } catch (TaskRejectedException e) {
            log.error("Executor rejected resume of analysis job, cleaning up DB record", e);
            asyncJobRepository.deleteJob(ASYNC_ID);
        } catch (Exception e) {
            log.error("Failed to resume analysis job on startup", e);
        }
    }

    /**
     * Attempts to start a new analysis job for the specified time range.
     *
     * @param days Number of days to look back for closed tickets to analyze
     * @return true if the job was started successfully, false if a job is already running
     */
    public boolean start(int days) {
        if (asyncJobRepository.tryStartJob(ASYNC_ID, Integer.toString(days))) {
            try {
                log.info("Started new async job: id={}, days={}", ASYNC_ID, days);
                applicationContext.getBean(AnalysisService.class).runAsyncAnalysis(days);
                return true;
            } catch (TaskRejectedException e) {
                log.error("Executor rejected analysis job, cleaning up DB record", e);
                asyncJobRepository.deleteJob(ASYNC_ID);
                return false;
            }
        } else {
            log.warn("Cannot start async job {}: already running", ASYNC_ID);
            return false;
        }
    }

    /**
     * Runs the analysis job asynchronously on the {@code analysisTaskExecutor}.
     *
     * <p>This method:
     * <ol>
     *   <li>Loads the prompt text from the configured file</li>
     *   <li>Computes the prompt ID as a SHA-256 hash of the prompt content</li>
     *   <li>Finds all threads that need analysis (closed tickets without analysis for this prompt ID)</li>
     *   <li>Analyzes each thread using the LLM</li>
     *   <li>Persists valid analysis results immediately</li>
     *   <li>Updates the in-memory status after each thread</li>
     *   <li>Applies rate limiting between LLM calls</li>
     *   <li>Cleans up the async job record when complete</li>
     * </ol>
     *
     * @param days Number of days to look back for closed tickets
     */
    @Async("analysisTaskExecutor")
    public void runAsyncAnalysis(int days) {

        try {
            String prompt = loadPrompt();
            String promptId = computePromptId(prompt);
            log.info("Computed prompt ID (SHA-256): {}", promptId);

            // Find threads that need analysis (no analysis record with this prompt ID)
            ImmutableList<ThreadToAnalyze> threads = threadsAwaitingAnalysisService.find(days, promptId);

            currentStatus.set(new AnalysisStatus(ASYNC_ID, threads.size(), 0, true, null));

            int analyzedCount = 0;
            boolean interrupted = false;

            // Analyze each thread
            for (ThreadToAnalyze thread : threads) {
                try {
                    AnalysisRecord record = llmAnalysisService.analyzeThread(
                            slackTicketsProps.channelId(), thread.threadTs(), thread.ticketId(), prompt);

                    if (record == null || !record.isValid()) {
                        log.warn("Skipping invalid analysis result for ticket {}", thread.ticketId());
                    } else {
                        // Add prompt ID to record
                        AnalysisRecord recordWithPromptId = new AnalysisRecord(
                                record.ticketId(),
                                record.driver(),
                                record.category(),
                                record.feature(),
                                record.summary(),
                                promptId);

                        // Persist immediately
                        analysisRepository.upsert(recordWithPromptId);

                        analyzedCount++;
                        currentStatus.set(new AnalysisStatus(ASYNC_ID, threads.size(), analyzedCount, true, null));

                        log.info("Analyzed thread {}/{}: ticket={}", analyzedCount, threads.size(), thread.ticketId());
                    }

                    // Rate limiting delay to avoid hitting LLM API limits
                    Thread.sleep(analysisProps.vertex().requestDelay().toMillis());

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Analysis interrupted at ticket {}", thread.ticketId());
                    interrupted = true;
                    break;
                } catch (Exception e) {
                    log.error("Failed to analyze thread for ticket {}: {}", thread.ticketId(), e.getMessage(), e);
                    // Continue with next thread
                }
            }

            if (interrupted) {
                log.warn("Async job {} interrupted: analyzed {}/{} threads", ASYNC_ID, analyzedCount, threads.size());
                currentStatus.set(new AnalysisStatus(
                        ASYNC_ID,
                        threads.size(),
                        analyzedCount,
                        false,
                        "Analysis interrupted after " + analyzedCount + "/" + threads.size() + " threads"));
            } else {
                log.info("Async job {} completed: analyzed {}/{} threads", ASYNC_ID, analyzedCount, threads.size());
                currentStatus.set(new AnalysisStatus(ASYNC_ID, threads.size(), analyzedCount, false, null));
            }

        } catch (Exception e) {
            log.error("Analysis job {} failed: {}", ASYNC_ID, e.getMessage(), e);
            currentStatus.set(new AnalysisStatus(ASYNC_ID, 0, 0, false, e.toString()));
        } finally {
            asyncJobRepository.deleteJob(ASYNC_ID);
        }
    }

    /**
     * Loads the prompt text from the file specified in {@link AnalysisProps#prompt()}.
     *
     * @return The prompt text content
     * @throws RuntimeException if the file cannot be read
     */
    private String loadPrompt() {
        try {
            String promptFile = analysisProps.prompt().file();
            return Files.readString(Path.of(promptFile));
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to load prompt file: " + analysisProps.prompt().file(), e);
        }
    }

    /**
     * Computes a prompt ID by hashing the prompt content with SHA-256.
     *
     * <p>This ensures the prompt ID automatically changes whenever the prompt content changes,
     * triggering re-analysis of threads with the updated prompt.
     *
     * @param promptContent The prompt text to hash
     * @return A 64-character lowercase hex string (SHA-256 digest)
     */
    static String computePromptId(String promptContent) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(promptContent.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 must be available in every JVM", e);
        }
    }

    /**
     * Gets the current status of the analysis job.
     *
     * @return Current analysis status, or null if no status is available
     */
    public AnalysisService.AnalysisStatus getStatus() {
        return currentStatus.get();
    }
}
