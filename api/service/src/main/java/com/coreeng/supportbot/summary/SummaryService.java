package com.coreeng.supportbot.summary;

import com.coreeng.supportbot.analysis.AnalysisPrompt;
import com.coreeng.supportbot.analysis.AnalysisPromptLoadException;
import com.coreeng.supportbot.analysis.AnalysisPromptRepository;
import com.coreeng.supportbot.analysis.AnalysisPromptType;
import com.coreeng.supportbot.analysis.AnalysisService;
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
    private final SummaryReadRepository summaryReadRepository;
    private final SummarySnapshotRepository summarySnapshotRepository;
    private final SummaryRefresher summaryRefresher;
    private final SlackChannelRegistry channelRegistry;

    /** Breakdowns plus whatever can be said about the prose summary right now. */
    public record SummaryResult(SummaryBreakdowns breakdowns, SummaryState summary) {}

    /**
     * The in-use prompt the summary prose is generated with, for the page's View Prompts dialog.
     *
     * @throws AnalysisPromptLoadException when no summary prompt version is marked as in use
     */
    public String promptContent() {
        AnalysisPrompt prompt = analysisPromptRepository.findInUse(AnalysisPromptType.SUMMARY);
        if (prompt == null) {
            throw new AnalysisPromptLoadException("No summary prompt version is marked as in use");
        }
        return prompt.content();
    }

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
        // A run in flight may be for a different window — the lock is global. Reporting `generating`
        // either way is honest (nothing can start until it finishes) and converges: the next poll
        // after it ends starts this window's refresh if it still needs one. Checked first: every open
        // tab polls while a run is in flight, and none of them needs the summary prompt read below.
        SummaryRefreshStatus refresh = summaryRefresher.status();
        if (refresh.running()) {
            return generating(refresh.phase());
        }

        AnalysisPrompt summaryPrompt = analysisPromptRepository.findInUse(AnalysisPromptType.SUMMARY);
        if (summaryPrompt == null) {
            return new SummaryState.Unavailable("No summary prompt version is marked as in use");
        }
        String summaryPromptId = AnalysisService.computePromptId(summaryPrompt.content());

        String fingerprint = summaryReadRepository
                .fingerprint(window, classificationPromptId, channelIds)
                .value();

        String failure = summaryRefresher.failureFor(window, fingerprint);
        if (failure != null) {
            // Retrying on every poll would hammer the LLM with the same failing input; the failure is
            // released as soon as the window's data changes.
            return new SummaryState.Unavailable(failure);
        }

        // The fingerprint covers the classification gaps too, so a snapshot generated after a backfill
        // that could not classify everything is still served: regenerating would only re-run the same
        // failing classifications and the same summary on every poll.
        SummarySnapshot snapshot = summarySnapshotRepository.find(window, summaryPromptId);
        if (snapshot != null && snapshot.fingerprint().equals(fingerprint)) {
            return new SummaryState.Ready(snapshot.content(), snapshot.model(), snapshot.generatedAt());
        }

        if (summaryRefresher.start(window)) {
            return generating(SummaryState.Phase.CLASSIFYING);
        }
        // Someone claimed the lock between the check above and here; report their run.
        return generating(summaryRefresher.status().phase());
    }

    private SummaryState.Generating generating(SummaryState.Phase phase) {
        if (phase != SummaryState.Phase.CLASSIFYING) {
            return new SummaryState.Generating(phase, null, null);
        }
        AnalysisService.AnalysisStatus status = analysisService.getStatus();
        if (!status.running()) {
            // The refresh has been dispatched but classify() has not started yet: the status still
            // holds the previous run's final counts, which would render as a complete progress bar.
            return new SummaryState.Generating(phase, null, null);
        }
        return new SummaryState.Generating(phase, status.analyzedCount(), status.exportedCount());
    }
}
