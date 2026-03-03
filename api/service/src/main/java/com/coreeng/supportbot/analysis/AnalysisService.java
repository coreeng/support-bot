package com.coreeng.supportbot.analysis;

import com.coreeng.supportbot.analysis.AnalysisRepository.AnalysisRecord;
import com.coreeng.supportbot.analysis.ThreadsAwaitingAnalysisRepository.ThreadToAnalyze;
import com.coreeng.supportbot.analysis.llm.LlmAnalysisService;
import com.coreeng.supportbot.asyncjob.AsyncJobRepository;
import com.coreeng.supportbot.config.AnalysisProps;
import com.coreeng.supportbot.config.SlackTicketsProps;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.ImmutableList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
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
 * when the prompt hasn't changed. The prompt ID is loaded from a YAML file specified in
 * {@link AnalysisProps}.
 */
@Service
@ConditionalOnProperty(name = "analysis.prompt.enabled", havingValue = "true")
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
    private final com.coreeng.supportbot.analysis.AnalysisRepository analysisRepository;
    private final AnalysisProps analysisProps;
    private final SlackTicketsProps slackTicketsProps;
    private final ApplicationContext applicationContext;

    public AnalysisService(
            AsyncJobRepository asyncJobRepository,
            ThreadsAwaitingAnalysisService threadsAwaitingAnalysisService,
            LlmAnalysisService llmAnalysisService,
            com.coreeng.supportbot.analysis.AnalysisRepository analysisRepository,
            AnalysisProps analysisProps,
            SlackTicketsProps slackTicketsProps,
            ApplicationContext applicationContext) {
        this.asyncJobRepository = asyncJobRepository;
        this.threadsAwaitingAnalysisService = threadsAwaitingAnalysisService;
        this.llmAnalysisService = llmAnalysisService;
        this.analysisRepository = analysisRepository;
        this.analysisProps = analysisProps;
        this.slackTicketsProps = slackTicketsProps;
        this.applicationContext = applicationContext;
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
        AsyncJobRepository.AsyncJob existingJob = asyncJobRepository.findJob(ASYNC_ID);

        if (existingJob != null) {
            log.info("Found pending async job on startup: {}, resuming...", ASYNC_ID);
            applicationContext.getBean(AnalysisService.class).runAsyncAnalysis(Integer.parseInt(existingJob.data()));
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
            log.info("Started new async job: id={}, days={}", ASYNC_ID, days);
            applicationContext.getBean(AnalysisService.class).runAsyncAnalysis(days);
            return true;
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
     *   <li>Loads the current prompt ID from the YAML file</li>
     *   <li>Finds all threads that need analysis (closed tickets without analysis for this prompt ID)</li>
     *   <li>Loads the prompt text from the configured file</li>
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
            String promptId = loadPromptId();
            // Find threads that need analysis (no analysis record with this prompt ID)
            ImmutableList<ThreadToAnalyze> threads = threadsAwaitingAnalysisService.find(days, promptId);

            currentStatus.set(new AnalysisStatus(ASYNC_ID, threads.size(), 0, true, null));

            String prompt = loadPrompt();

            int analyzedCount = 0;

            // Analyze each thread
            for (ThreadToAnalyze thread : threads) {
                try {
                    AnalysisRecord record = llmAnalysisService.analyzeThread(
                            slackTicketsProps.channelId(), thread.threadTs(), thread.ticketId(), prompt);

                    if (record != null && record.isValid()) {
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

                } catch (Exception e) {
                    log.error("Failed to analyze thread for ticket {}: {}", thread.ticketId(), e.getMessage());
                    // Continue with next thread
                }
            }

            // Success
            log.info("Async job {} completed: analyzed {}/{} threads", ASYNC_ID, analyzedCount, threads.size());
            currentStatus.set(new AnalysisStatus(ASYNC_ID, threads.size(), analyzedCount, false, null));

        } catch (Exception e) {
            log.error("Analysis job {} failed: {}", ASYNC_ID, e.getMessage(), e);
            currentStatus.set(new AnalysisStatus(ASYNC_ID, 0, 0, false, e.getMessage()));
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
     * Loads the prompt ID from the YAML file specified in {@link AnalysisProps#prompt()}.
     *
     * <p>The YAML file must contain an {@code id} field. This ID is used to version prompts
     * and avoid re-analyzing threads when the prompt hasn't changed.
     *
     * @return The prompt ID string
     * @throws RuntimeException if the file cannot be read or doesn't contain an 'id' field
     */
    private String loadPromptId() {
        try {
            String idFile = analysisProps.prompt().idFile();
            String yamlContent = Files.readString(Path.of(idFile));

            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            @SuppressWarnings("unchecked")
            Map<String, Object> yamlData = yamlMapper.readValue(yamlContent, Map.class);

            Object id = yamlData.get("id");
            if (id == null) {
                throw new IllegalArgumentException("Prompt ID file does not contain 'id' field: " + idFile);
            }

            return id.toString();
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to load prompt ID file: " + analysisProps.prompt().idFile(), e);
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
