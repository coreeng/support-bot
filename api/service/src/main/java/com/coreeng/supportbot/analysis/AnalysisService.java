package com.coreeng.supportbot.analysis;

import com.coreeng.supportbot.analysis.ThreadsAwaitingAnalysisRepository.ThreadToAnalyze;
import com.coreeng.supportbot.analysis.llm.LlmAnalysisService;
import com.coreeng.supportbot.asyncjob.AsyncJobRepository;
import com.coreeng.supportbot.config.ConditionalOnLlmEnabled;
import com.coreeng.supportbot.config.LlmProps;
import com.google.common.collect.ImmutableList;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Service for orchestrating LLM-powered analysis of support tickets.
 *
 * <p>This service manages the lifecycle of analysis jobs, including:
 * <ul>
 *   <li>Starting new analysis jobs with database-level concurrency control via {@link AsyncJobRepository}</li>
 *   <li>Asynchronous processing of tickets using {@link LlmAnalysisService}</li>
 *   <li>Incremental persistence of analysis results to {@link AnalysisRepository}</li>
 *   <li>In-memory status tracking for progress monitoring via {@link AnalysisStatus}</li>
 * </ul>
 *
 * <p>Concurrency is controlled via a unique constraint on the {@code async_job} table,
 * ensuring only one analysis job can run at a time. A job interrupted by a restart is picked up
 * again by {@link AnalysisJobResumer}.
 *
 * <p>The analysis process runs asynchronously on a dedicated single-threaded executor
 * ({@code analysisTaskExecutor}) to avoid LLM rate limits and prevent double processing.
 *
 * <p>The service uses prompt versioning via {@code prompt_id} to avoid re-analyzing threads
 * when the prompt hasn't changed. The prompt ID is computed as a SHA-256 hash of the in-use
 * prompt's text, so it auto-updates whenever the prompt changes.
 */
@Service
@ConditionalOnLlmEnabled
@Slf4j
public class AnalysisService {

    private static final String ASYNC_ID = AnalysisJobData.JOB_ID;

    /**
     * Repository for managing async job state in the database.
     * Used to track running analysis jobs, prevent concurrent runs, and enable resume on startup.
     */
    private final AsyncJobRepository asyncJobRepository;

    private final ThreadsAwaitingAnalysisService threadsAwaitingAnalysisService;
    private final LlmAnalysisService llmAnalysisService;
    private final AnalysisRepository analysisRepository;
    private final AnalysisPromptRepository analysisPromptRepository;
    private final LlmProps llmProps;

    /** The single-threaded {@code analysisTaskExecutor}; every run, days-based or windowed, goes through it. */
    private final Executor analysisExecutor;

    public AnalysisService(
            AsyncJobRepository asyncJobRepository,
            ThreadsAwaitingAnalysisService threadsAwaitingAnalysisService,
            LlmAnalysisService llmAnalysisService,
            AnalysisRepository analysisRepository,
            AnalysisPromptRepository analysisPromptRepository,
            LlmProps llmProps,
            @Qualifier("analysisTaskExecutor") Executor analysisExecutor) {
        this.asyncJobRepository = asyncJobRepository;
        this.threadsAwaitingAnalysisService = threadsAwaitingAnalysisService;
        this.llmAnalysisService = llmAnalysisService;
        this.analysisRepository = analysisRepository;
        this.analysisPromptRepository = analysisPromptRepository;
        this.llmProps = llmProps;
        this.analysisExecutor = analysisExecutor;
    }

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
     * Attempts to start a new analysis job for the specified time range.
     *
     * @param days Number of days to look back for closed tickets to analyze
     * @return true if the job was started successfully, false if a job is already running
     */
    public boolean start(int days) {
        if (!asyncJobRepository.tryStartJob(ASYNC_ID, AnalysisJobData.days(days))) {
            log.warn("Cannot start async job {}: already running", ASYNC_ID);
            return false;
        }
        log.info("Started new async job: id={}, days={}", ASYNC_ID, days);
        return dispatch(days);
    }

    /**
     * Resumes a days-based run whose {@code async_job} row already exists — a run a restart
     * interrupted. Unlike {@link #start} it does not claim the lock, because the row is the lock.
     *
     * @return false if the executor rejected the run; the row has then been deleted
     */
    public boolean resume(int days) {
        return dispatch(days);
    }

    /**
     * Hands the run to the analysis executor. The caller holds the lock row; if the executor will
     * not take the run, the row is released here so it does not block every later run.
     */
    private boolean dispatch(int days) {
        try {
            analysisExecutor.execute(() -> runAnalysis(days));
            return true;
        } catch (RejectedExecutionException e) {
            log.error("Executor rejected analysis job, cleaning up DB record", e);
            asyncJobRepository.deleteJob(ASYNC_ID);
            return false;
        }
    }

    /**
     * Runs the analysis job on the calling thread — {@link #start} and {@link #resume} put it on the
     * {@code analysisTaskExecutor} — and releases the {@code async_job} lock when done.
     *
     * <p>This method:
     * <ol>
     *   <li>Loads the prompt text from the version marked as in use</li>
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
    public void runAnalysis(int days) {
        try {
            String prompt = loadPrompt();
            String promptId = computePromptId(prompt);
            log.info("Computed prompt ID (SHA-256): {}", promptId);

            // Find threads that need analysis (no analysis record with this prompt ID)
            classify(threadsAwaitingAnalysisService.find(days, promptId), prompt, promptId);
        } catch (Exception e) {
            log.error("Analysis job {} failed: {}", ASYNC_ID, e.getMessage(), e);
            currentStatus.set(new AnalysisStatus(ASYNC_ID, 0, 0, false, e.toString()));
        } finally {
            asyncJobRepository.deleteJob(ASYNC_ID);
        }
    }

    /**
     * Classifies the tickets raised in the given window that have no analysis for the current prompt.
     *
     * <p>Runs on the caller's thread and deliberately neither takes nor releases the {@code async_job}
     * lock: the Support Summary refresh that calls this already holds it and goes on to generate the
     * prose summary afterwards, so releasing here would let a second run start mid-refresh. Failures
     * propagate for the same reason — the caller decides what a failed backfill means for the page.
     *
     * @param from First day of the window (inclusive), on ticket-creation time
     * @param to Last day of the window (inclusive)
     */
    public void backfillWindow(LocalDate from, LocalDate to) {
        String prompt = loadPrompt();
        String promptId = computePromptId(prompt);
        log.info("Backfilling analysis for window {}..{} with prompt ID {}", from, to, promptId);
        classify(threadsAwaitingAnalysisService.find(from, to, promptId), prompt, promptId);
    }

    /**
     * Analyses each thread and persists the result immediately, so an interrupted run keeps whatever
     * it already produced. A thread that fails is logged and skipped rather than aborting the run.
     */
    private void classify(ImmutableList<ThreadToAnalyze> threads, String prompt, String promptId) {
        currentStatus.set(new AnalysisStatus(ASYNC_ID, threads.size(), 0, true, null));

        int analyzedCount = 0;
        boolean interrupted = false;

        for (ThreadToAnalyze thread : threads) {
            try {
                AnalysisRecord record = llmAnalysisService.analyzeThread(
                        thread.channelId(), thread.threadTs(), thread.ticketId(), prompt);

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
                Thread.sleep(llmProps.requestDelay().toMillis());

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
    }

    /**
     * Loads the text of the classification prompt version currently marked as in use.
     *
     * @return The prompt text content
     * @throws AnalysisPromptLoadException if no prompt version is marked as in use
     */
    public String loadPrompt() {
        AnalysisPrompt prompt = inUsePrompt(AnalysisPromptType.CLASSIFICATION);
        if (prompt == null) {
            throw new AnalysisPromptLoadException("No analysis prompt version is marked as in use");
        }
        return prompt.content();
    }

    /**
     * The identity of the classification prompt currently in use: the {@link #computePromptId hash}
     * of its text, which is what {@code analysis.prompt_id} holds and what every read of the analysis
     * rows must be keyed on.
     *
     * @throws AnalysisPromptLoadException if no classification prompt version is marked as in use
     */
    public String currentPromptId() {
        return computePromptId(loadPrompt());
    }

    /**
     * The prompt version of the given type currently marked as in use. The one door to the prompt
     * store for other features (the Support Summary reads its own prompt through here), so a change
     * in how prompts are stored stays inside this package.
     *
     * @return the in-use prompt, or null if no version of that type is marked as in use
     * @throws AnalysisPromptLoadException if the prompt store could not be read
     */
    public @Nullable AnalysisPrompt inUsePrompt(AnalysisPromptType type) {
        try {
            return analysisPromptRepository.findInUse(type);
        } catch (RuntimeException e) {
            throw new AnalysisPromptLoadException("Failed to read the " + type.dbValue() + " prompt", e);
        }
    }

    /**
     * Computes a prompt ID by hashing the prompt content with SHA-256.
     *
     * <p>This ensures the prompt ID automatically changes whenever the prompt content changes,
     * triggering re-analysis of threads with the updated prompt.
     *
     * <p>Public because the same identity rule applies to the summary prompt: its hash keys the
     * cached snapshots, so a prompt edit produces a new cache entry rather than silently reusing prose
     * written by the previous version.
     *
     * @param promptContent The prompt text to hash
     * @return A 64-character lowercase hex string (SHA-256 digest)
     */
    public static String computePromptId(String promptContent) {
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
