package com.coreeng.supportbot.summary;

import com.coreeng.supportbot.analysis.AnalysisPrompt;
import com.coreeng.supportbot.analysis.AnalysisPromptLoadException;
import com.coreeng.supportbot.analysis.AnalysisPromptType;
import com.coreeng.supportbot.analysis.AnalysisService;
import com.coreeng.supportbot.config.ConditionalOnLlmEnabled;
import com.coreeng.supportbot.config.SlackChannelRegistry;
import com.coreeng.supportbot.config.SummaryAreaProps;
import com.coreeng.supportbot.teamgroups.TeamGroup;
import com.coreeng.supportbot.teamgroups.TeamGroupService;
import com.google.common.collect.ImmutableList;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
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
@ConditionalOnLlmEnabled
@RequiredArgsConstructor
@Slf4j
public class SummaryService {

    private final AnalysisService analysisService;
    private final SummaryReadRepository summaryReadRepository;
    private final SummarySnapshotRepository summarySnapshotRepository;
    private final SummaryRefreshService summaryRefresher;
    private final SlackChannelRegistry channelRegistry;
    private final TeamGroupService teamGroupService;
    private final SummaryAreaProps summaryAreaProps;
    private final Clock clock;

    /** Breakdowns plus whatever can be said about the prose summary right now, for the given scope. */
    public record SummaryResult(SummaryScope scope, SummaryBreakdowns breakdowns, SummaryState summary) {}

    /**
     * The in-use prompt the summary prose is generated with, for the page's View Prompts dialog.
     *
     * @throws AnalysisPromptLoadException when no summary prompt version is marked as in use
     */
    public String promptContent() {
        AnalysisPrompt prompt = analysisService.inUsePrompt(AnalysisPromptType.SUMMARY);
        if (prompt == null) {
            throw new AnalysisPromptLoadException("No summary prompt version is marked as in use");
        }
        return prompt.content();
    }

    /**
     * @param teamGroup the code of the team group to narrow the summary to; null or blank for every
     *     ticket in the window
     * @throws UnknownTeamGroupException when no team group has that code
     */
    public SummaryResult get(LocalDate from, LocalDate to, @Nullable String teamGroup) {
        SummaryScope scope = new SummaryScope(new SummaryWindow(from, to), resolveTeamGroup(teamGroup));
        String classificationPromptId = analysisService.currentPromptId();
        ImmutableList<String> channelIds = channelRegistry.monitoredChannelIds();

        SummaryBreakdowns breakdowns = summaryReadRepository.breakdowns(scope, classificationPromptId, channelIds);
        SummaryState state = resolveSummaryState(scope, classificationPromptId, channelIds);
        return new SummaryResult(scope, breakdowns, state);
    }

    private @Nullable TeamGroup resolveTeamGroup(@Nullable String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        TeamGroup group = teamGroupService.find(code);
        if (group == null) {
            throw new UnknownTeamGroupException(code);
        }
        return group;
    }

    private SummaryState resolveSummaryState(
            SummaryScope scope, String classificationPromptId, ImmutableList<String> channelIds) {
        // A run in flight may be for a different scope — the lock is global. Reporting `generating`
        // either way is honest (nothing can start until it finishes) and converges: the next poll
        // after it ends starts this scope's refresh if it still needs one. Checked first: every open
        // tab polls while a run is in flight, and none of them needs the summary prompt read below.
        SummaryRefreshStatus refresh = summaryRefresher.status();
        if (refresh.running()) {
            return generating(refresh.phase());
        }

        AnalysisPrompt summaryPrompt = analysisService.inUsePrompt(AnalysisPromptType.SUMMARY);
        if (summaryPrompt == null) {
            return new SummaryState.Unavailable("No summary prompt version is marked as in use");
        }
        String summaryPromptId = AnalysisService.computePromptId(summaryPrompt.content());

        SummaryFingerprint fingerprint = summaryReadRepository.fingerprint(scope, classificationPromptId, channelIds);

        String failure = summaryRefresher.failureFor(scope, summaryPromptId, fingerprint.value());
        if (failure != null) {
            // Retrying on every poll would hammer the LLM with the same failing input; the failure is
            // released as soon as the scope's data or the summary prompt changes, or the retry delay
            // passes.
            return new SummaryState.Unavailable(failure);
        }

        // The fingerprint covers the classification gaps too, so a snapshot generated after a backfill
        // that could not classify everything is still served: regenerating on every poll would only
        // re-run the same failing classifications and the same summary.
        SummarySnapshot snapshot = summarySnapshotRepository.find(scope, summaryPromptId);
        if (snapshot != null
                && snapshot.fingerprint().equals(fingerprint.value())
                && !gapsDueForRetry(snapshot, fingerprint)) {
            return new SummaryState.Ready(snapshot.content(), snapshot.model(), snapshot.generatedAt());
        }

        if (summaryRefresher.start(scope)) {
            return generating(SummaryState.Phase.CLASSIFYING);
        }
        // Someone claimed the lock between the check above and here; report their run.
        return generating(summaryRefresher.status().phase());
    }

    /**
     * Whether a snapshot that matches the window's data should nevertheless be regenerated because
     * the gaps baked into it are old enough to try again.
     *
     * <p>A gap is a closed ticket the backfill could not classify. Some never will be (the Slack
     * thread is gone), but many are transient — a rate limit, a timeout — and pinning them into the
     * fingerprint until the window's data happens to change would leave a finished window
     * permanently short. So once the snapshot is older than {@link SummaryAreaProps.Page#failureRetryDelay()}
     * its gaps are treated as stale and the next visit starts a refresh that attempts them again. The
     * refresh stores a new snapshot either way, and its {@code generatedAt} restarts the clock, so a
     * gap that still cannot be filled costs one retry per delay rather than one per poll.
     */
    private boolean gapsDueForRetry(SummarySnapshot snapshot, SummaryFingerprint fingerprint) {
        Instant generatedAt = snapshot.generatedAt();
        if (fingerprint.gapCount() == 0 || generatedAt == null) {
            return false;
        }
        boolean due = !clock.instant()
                .isBefore(generatedAt.plus(summaryAreaProps.page().failureRetryDelay()));
        if (due) {
            log.info(
                    "Snapshot for window {}..{} (team group '{}') carries {} classification gap(s) and is older"
                            + " than {}; retrying them",
                    snapshot.window().from(),
                    snapshot.window().to(),
                    snapshot.teamGroup(),
                    fingerprint.gapCount(),
                    summaryAreaProps.page().failureRetryDelay());
        }
        return due;
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
