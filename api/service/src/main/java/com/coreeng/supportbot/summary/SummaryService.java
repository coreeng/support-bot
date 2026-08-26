package com.coreeng.supportbot.summary;

import com.coreeng.supportbot.analysis.AnalysisPrompt;
import com.coreeng.supportbot.analysis.AnalysisPromptRepository;
import com.coreeng.supportbot.analysis.AnalysisPromptType;
import com.coreeng.supportbot.analysis.AnalysisService;
import com.coreeng.supportbot.analysis.ThreadsAwaitingAnalysisService;
import com.coreeng.supportbot.config.SlackChannelRegistry;
import com.google.common.collect.ImmutableList;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Serves the Support Summary page and decides, on each visit, whether work needs to start.
 *
 * <p>There is no run button: the breakdowns are computed and returned immediately, and if the window
 * has classification gaps or a stale prose summary a refresh is kicked off server-side while the
 * response is being built. That keeps the trigger off the SUPPORT_ENGINEER-only {@code /analysis/run}
 * endpoint, so leadership viewers can cause a backfill without being granted that permission.
 *
 * <p>A failing summary never costs the caller the breakdowns — the summary section carries its own
 * state.
 */
@Service
@ConditionalOnProperty(name = "summary.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SummaryService {

    private final AnalysisService analysisService;
    private final AnalysisPromptRepository analysisPromptRepository;
    private final ThreadsAwaitingAnalysisService threadsAwaitingAnalysisService;
    private final SummaryReadRepository summaryReadRepository;
    private final SummarySnapshotRepository summarySnapshotRepository;
    private final SummaryRefreshService summaryRefreshService;
    private final SlackChannelRegistry channelRegistry;

    /** Breakdowns plus whatever can be said about the prose summary right now. */
    public record SummaryResult(SummaryBreakdowns breakdowns, SummaryState summary) {}

    public SummaryResult get(LocalDate from, LocalDate to) {
        SummaryWindow window = new SummaryWindow(from, to);
        String classificationPromptId = AnalysisService.computePromptId(analysisService.loadPrompt());
        ImmutableList<String> channelIds = channelRegistry.monitoredChannelIds();

        SummaryBreakdowns breakdowns = summaryReadRepository.breakdowns(window, classificationPromptId, channelIds);
        SummaryState state = resolveSummaryState(window, classificationPromptId, channelIds);
        return new SummaryResult(breakdowns, state);
    }

    private SummaryState resolveSummaryState(
            SummaryWindow window, String classificationPromptId, ImmutableList<String> channelIds) {
        AnalysisPrompt summaryPrompt = analysisPromptRepository.findInUse(AnalysisPromptType.SUMMARY);
        if (summaryPrompt == null) {
            return new SummaryState.Unavailable("No summary prompt version is marked as in use");
        }
        String summaryPromptId = AnalysisService.computePromptId(summaryPrompt.content());

        // A run in flight may be for a different window — the lock is global. Reporting `generating`
        // either way is honest (nothing can start until it finishes) and converges: the next poll
        // after it ends starts this window's refresh if it still needs one.
        SummaryRefreshService.RefreshStatus refresh = summaryRefreshService.status();
        if (refresh.running()) {
            return generating(refresh.phase());
        }

        String fingerprint = summaryReadRepository
                .fingerprint(window, classificationPromptId, channelIds)
                .value();

        String failure = summaryRefreshService.failureFor(window, fingerprint);
        if (failure != null) {
            // Retrying on every poll would hammer the LLM with the same failing input; the failure is
            // released as soon as the window's data changes.
            return new SummaryState.Unavailable(failure);
        }

        SummarySnapshot snapshot = summarySnapshotRepository.find(window, summaryPromptId);
        if (snapshot != null
                && snapshot.fingerprint().equals(fingerprint)
                && !hasClassificationGaps(window, classificationPromptId)) {
            return new SummaryState.Ready(snapshot.content(), snapshot.model(), snapshot.generatedAt());
        }

        if (summaryRefreshService.start(window)) {
            return generating(SummaryState.Phase.CLASSIFYING);
        }
        // Someone claimed the lock between the check above and here; report their run.
        return generating(summaryRefreshService.status().phase());
    }

    private boolean hasClassificationGaps(SummaryWindow window, String classificationPromptId) {
        return !threadsAwaitingAnalysisService
                .find(window.from(), window.to(), classificationPromptId)
                .isEmpty();
    }

    private SummaryState.Generating generating(SummaryState.Phase phase) {
        if (phase != SummaryState.Phase.CLASSIFYING) {
            return new SummaryState.Generating(phase, null, null);
        }
        AnalysisService.AnalysisStatus status = analysisService.getStatus();
        return new SummaryState.Generating(phase, status.analyzedCount(), status.exportedCount());
    }
}
