package com.coreeng.supportbot.summary;

import com.coreeng.supportbot.analysis.AnalysisJobData;
import com.coreeng.supportbot.analysis.AnalysisPrompt;
import com.coreeng.supportbot.analysis.AnalysisPromptLoadException;
import com.coreeng.supportbot.analysis.AnalysisPromptRepository;
import com.coreeng.supportbot.analysis.AnalysisPromptType;
import com.coreeng.supportbot.analysis.AnalysisService;
import com.coreeng.supportbot.analysis.WindowAnalysisRunner;
import com.coreeng.supportbot.asyncjob.AsyncJobRepository;
import com.coreeng.supportbot.config.SlackChannelRegistry;
import com.coreeng.supportbot.config.SummaryProps;
import com.google.common.collect.ImmutableList;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Runs one window's refresh: backfill the classification gaps, then regenerate the prose summary.
 *
 * <p>Both halves happen inside a single {@code async_job} claim on the shared {@code "analysis"}
 * lock. Splitting them would let a days-based run slip in between and change the very data the
 * summary is about to be generated from, and would show the user two separate waits instead of one.
 *
 * <p>The lock also means concurrent visitors never duplicate work: the first claim wins, everyone
 * else sees {@code generating} and re-polls.
 */
@Service
@ConditionalOnProperty(name = "summary.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SummaryRefreshService implements WindowAnalysisRunner {

    private static final String ASYNC_ID = "analysis";

    private static final RefreshStatus IDLE = new RefreshStatus(null, SummaryState.Phase.CLASSIFYING, false);

    private final AsyncJobRepository asyncJobRepository;
    private final AnalysisService analysisService;
    private final AnalysisPromptRepository analysisPromptRepository;
    private final SummaryReadRepository summaryReadRepository;
    private final SummarySnapshotRepository summarySnapshotRepository;
    private final LlmSummaryService llmSummaryService;
    private final SlackChannelRegistry channelRegistry;
    private final SummaryProps summaryProps;
    private final ApplicationContext applicationContext;

    private final AtomicReference<RefreshStatus> status = new AtomicReference<>(IDLE);
    private final AtomicReference<@Nullable Failure> lastFailure = new AtomicReference<>();

    /** What the in-flight refresh, if any, is doing. */
    public record RefreshStatus(@Nullable SummaryWindow window, SummaryState.Phase phase, boolean running) {}

    /**
     * A failed attempt, remembered so the page reports the error instead of retrying on every poll.
     * It is keyed on the fingerprint as well as the window: once the window's data changes the
     * failure is no longer about the same input and is worth retrying.
     */
    private record Failure(SummaryWindow window, String fingerprint, String error) {}

    /**
     * Claims the shared lock and kicks off a refresh.
     *
     * @return false when another run already holds the lock, or the executor is saturated
     */
    public boolean start(SummaryWindow window) {
        if (!asyncJobRepository.tryStartJob(ASYNC_ID, AnalysisJobData.window(window.from(), window.to()))) {
            log.debug(
                    "Summary refresh for {}..{} not started: a job already holds the lock", window.from(), window.to());
            return false;
        }
        try {
            log.info("Started summary refresh for window {}..{}", window.from(), window.to());
            // Through the context so the @Async proxy applies — a direct call would run inline.
            applicationContext.getBean(SummaryRefreshService.class).runWindowRefresh(window.from(), window.to());
            return true;
        } catch (TaskRejectedException e) {
            log.error("Executor rejected summary refresh, cleaning up DB record", e);
            asyncJobRepository.deleteJob(ASYNC_ID);
            return false;
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Never throws: a failed refresh must leave the breakdowns renderable, so the error is
     * recorded for the page to show and the lock is always released.
     */
    @Override
    @Async("analysisTaskExecutor")
    public void runWindowRefresh(LocalDate from, LocalDate to) {
        SummaryWindow window = new SummaryWindow(from, to);
        try {
            status.set(new RefreshStatus(window, SummaryState.Phase.CLASSIFYING, true));
            analysisService.backfillWindow(from, to);

            status.set(new RefreshStatus(window, SummaryState.Phase.SUMMARISING, true));
            generate(window);

            clearFailure(window);
            log.info("Summary refresh for window {}..{} completed", from, to);
        } catch (Exception e) {
            log.error("Summary refresh for window {}..{} failed: {}", from, to, e.getMessage(), e);
            recordFailure(window, e);
        } finally {
            status.set(IDLE);
            asyncJobRepository.deleteJob(ASYNC_ID);
        }
    }

    /** The current refresh state; {@code running=false} when nothing is in flight. */
    public RefreshStatus status() {
        return status.get();
    }

    /**
     * @return the recorded error for this window and data fingerprint, or null when the last attempt
     *     did not fail or the data has moved on since it did
     */
    public @Nullable String failureFor(SummaryWindow window, String fingerprint) {
        Failure failure = lastFailure.get();
        if (failure == null
                || !failure.window().equals(window)
                || !failure.fingerprint().equals(fingerprint)) {
            return null;
        }
        return failure.error();
    }

    /**
     * Reads the window afresh — after the backfill, so the counts, the reasons and the fingerprint
     * stored with the summary all describe the same post-backfill state.
     */
    private void generate(SummaryWindow window) {
        AnalysisPrompt summaryPrompt = analysisPromptRepository.findInUse(AnalysisPromptType.SUMMARY);
        if (summaryPrompt == null) {
            throw new AnalysisPromptLoadException("No summary prompt version is marked as in use");
        }
        String summaryPromptId = AnalysisService.computePromptId(summaryPrompt.content());
        String classificationPromptId = AnalysisService.computePromptId(analysisService.loadPrompt());
        ImmutableList<String> channelIds = channelRegistry.monitoredChannelIds();

        SummaryBreakdowns breakdowns = summaryReadRepository.breakdowns(window, classificationPromptId, channelIds);
        SummaryFingerprint fingerprint = summaryReadRepository.fingerprint(window, classificationPromptId, channelIds);
        ImmutableList<String> reasons =
                summaryReadRepository.reasons(window, classificationPromptId, channelIds, summaryProps.maxReasons());

        String content = llmSummaryService.generate(summaryPrompt.content(), breakdowns, reasons);
        if (content.isBlank()) {
            throw new IllegalStateException("The model returned an empty summary");
        }

        summarySnapshotRepository.upsert(new SummarySnapshot(
                window, summaryPromptId, fingerprint.value(), content.strip(), llmSummaryService.modelName(), null));
    }

    private void recordFailure(SummaryWindow window, Exception cause) {
        String fingerprint;
        try {
            fingerprint = summaryReadRepository
                    .fingerprint(
                            window,
                            AnalysisService.computePromptId(analysisService.loadPrompt()),
                            channelRegistry.monitoredChannelIds())
                    .value();
        } catch (Exception e) {
            // Without a fingerprint the failure cannot be tied to a data state, so let the next visit
            // retry rather than pinning an error we can no longer invalidate.
            log.warn("Could not fingerprint window {}..{} after a failed refresh", window.from(), window.to(), e);
            return;
        }
        lastFailure.set(new Failure(window, fingerprint, describe(cause)));
    }

    private void clearFailure(SummaryWindow window) {
        lastFailure.updateAndGet(failure -> failure != null && failure.window().equals(window) ? null : failure);
    }

    private static String describe(Exception cause) {
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }
}
