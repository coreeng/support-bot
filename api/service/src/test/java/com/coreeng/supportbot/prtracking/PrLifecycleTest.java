package com.coreeng.supportbot.prtracking;

import static com.coreeng.supportbot.dbschema.enums.PrTrackingStatus.APPROVED;
import static com.coreeng.supportbot.dbschema.enums.PrTrackingStatus.AWAITING_MERGE;
import static com.coreeng.supportbot.dbschema.enums.PrTrackingStatus.CHANGES_REQUESTED;
import static com.coreeng.supportbot.dbschema.enums.PrTrackingStatus.CLOSED;
import static com.coreeng.supportbot.dbschema.enums.PrTrackingStatus.ESCALATED;
import static com.coreeng.supportbot.dbschema.enums.PrTrackingStatus.MERGE_ESCALATED;
import static com.coreeng.supportbot.dbschema.enums.PrTrackingStatus.OPEN;
import static com.coreeng.supportbot.prtracking.PrLifecycle.Effect.ESCALATE;
import static com.coreeng.supportbot.prtracking.PrLifecycle.Effect.ESCALATE_MERGE;
import static com.coreeng.supportbot.prtracking.PrLifecycle.Effect.NOTIFY_APPROVED;
import static com.coreeng.supportbot.prtracking.PrLifecycle.Effect.NOTIFY_AWAITING_MERGE;
import static com.coreeng.supportbot.prtracking.PrLifecycle.Effect.NOTIFY_CHANGES_REQUESTED;
import static com.coreeng.supportbot.prtracking.PrLifecycle.Effect.NOTIFY_CLOSED;
import static com.coreeng.supportbot.prtracking.PrLifecycle.Effect.NOTIFY_MERGED;
import static com.coreeng.supportbot.prtracking.PrLifecycle.SlaOp.NONE;
import static com.coreeng.supportbot.prtracking.PrLifecycle.SlaOp.RESUME;
import static com.coreeng.supportbot.prtracking.PrLifecycle.SlaOp.SET_CLOSED_AT;
import static com.coreeng.supportbot.prtracking.PrLifecycle.SlaOp.START;
import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.dbschema.enums.PrTrackingStatus;
import com.coreeng.supportbot.prtracking.PrLifecycle.Decision;
import com.coreeng.supportbot.prtracking.PrLifecycle.Effect;
import com.coreeng.supportbot.prtracking.PrLifecycle.Observation;
import com.coreeng.supportbot.prtracking.PrLifecycle.SlaOp;
import com.coreeng.supportbot.prtracking.PrLifecycle.Verdict;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link PrLifecycle#decide} — no mocks. One case per {@link
 * PrLifecycle#TRANSITIONS} row, plus every implicit no-op and the subtle edges that justify the
 * refactor. The end-to-end side-effect wiring is covered by {@code PrLifecyclePollerTest}.
 */
class PrLifecycleTest {

    private static final Duration REMAINING = Duration.ofHours(2);
    private static final Duration STORED = Duration.ofHours(3);

    @Nested
    class ClosedFromAnyState {

        @Test
        void mergedClosesFromEveryActiveState() {
            for (PrTrackingStatus state : new PrTrackingStatus[] {OPEN, CHANGES_REQUESTED, APPROVED, ESCALATED}) {
                assertDecision(decide(obs(state).merged()), CLOSED, SET_CLOSED_AT, NOTIFY_MERGED);
            }
        }

        @Test
        void closedNotMergedClosesFromEveryActiveState() {
            for (PrTrackingStatus state : new PrTrackingStatus[] {OPEN, CHANGES_REQUESTED, APPROVED, ESCALATED}) {
                assertDecision(decide(obs(state).closed()), CLOSED, SET_CLOSED_AT, NOTIFY_CLOSED);
            }
        }

        @Test
        void closedBeatsAnyVerdict() {
            // merged + approved + mergeable: the closed row is evaluated first, so it still notifies merged.
            assertDecision(decide(obs(OPEN).merged().approved().mergeable()), CLOSED, SET_CLOSED_AT, NOTIFY_MERGED);
        }
    }

    @Nested
    class OpenState {

        @Test
        void changesRequestedWithLiveDeadlinePauses() {
            assertDecision(
                    decide(obs(OPEN).changesRequested().liveDeadline(REMAINING)),
                    CHANGES_REQUESTED,
                    new SlaOp.Pause(REMAINING),
                    NOTIFY_CHANGES_REQUESTED);
        }

        @Test
        void changesRequestedOnNoSlaRecordWritesStatusWithoutSla() {
            assertDecision(decide(obs(OPEN).changesRequested()), CHANGES_REQUESTED, NONE, NOTIFY_CHANGES_REQUESTED);
        }

        @Test
        void changesRequestedWhilePausedNotifiesOnlyAndDoesNotWriteStatus() {
            // The subtle edge: no live deadline but a stored remaining => notify, stay OPEN, no write.
            assertDecision(
                    decide(obs(OPEN).changesRequested().storedRemaining(STORED)), OPEN, NONE, NOTIFY_CHANGES_REQUESTED);
        }

        @Test
        void approvedAndMergeableCloses() {
            assertDecision(decide(obs(OPEN).approved().mergeable()), CLOSED, SET_CLOSED_AT, NOTIFY_APPROVED);
        }

        @Test
        void approvedNotMergeableWithLiveDeadlinePauses() {
            assertDecision(decide(obs(OPEN).approved().liveDeadline(REMAINING)), APPROVED, new SlaOp.Pause(REMAINING));
        }

        @Test
        void approvedNotMergeableOnNoSlaRecordWritesStatusWithoutSla() {
            assertDecision(decide(obs(OPEN).approved()), APPROVED, NONE);
        }

        @Test
        void noVerdictWithBreachedSlaEscalates() {
            assertDecision(decide(obs(OPEN).slaBreached()), ESCALATED, NONE, ESCALATE);
        }

        @Test
        void noVerdictWithLiveDeadlineNotYetBreachedIsNoOp() {
            assertDecision(decide(obs(OPEN).liveDeadline(REMAINING)), OPEN, NONE);
        }

        @Test
        void noSlaRecordNeverEscalates() {
            // observe() can only set slaBreached when there is a live deadline, so a no-SLA record
            // (no deadline, no stored remaining) with no verdict is always a no-op — never ESCALATED.
            assertDecision(decide(obs(OPEN)), OPEN, NONE);
        }

        @Test
        void mergeableWithNoVerdictIsNoOp() {
            // The most common live state: a mergeable PR no team member has reviewed yet. Isolates the
            // approved() conjunct of the "approved + mergeable" row — without it, every unreviewed
            // mergeable PR would be wrongly CLOSED + NOTIFY_APPROVED.
            assertDecision(decide(obs(OPEN).mergeable()), OPEN, NONE);
        }

        @Test
        void pausedRecordWithNoVerdictIsNoOp() {
            // A paused OPEN record (stored remaining, no live deadline) whose review was dismissed.
            // Isolates the changesRequested() conjunct of the "SLA paused (notify only)" row — without
            // it, a verdict-less paused record would emit a spurious NOTIFY_CHANGES_REQUESTED.
            assertDecision(decide(obs(OPEN).storedRemaining(STORED)), OPEN, NONE);
        }
    }

    @Nested
    class ChangesRequestedState {

        @Test
        void approvedAndMergeableCloses() {
            assertDecision(
                    decide(obs(CHANGES_REQUESTED).approved().mergeable()), CLOSED, SET_CLOSED_AT, NOTIFY_APPROVED);
        }

        @Test
        void approvedNotMergeableTransitionsToApprovedWithoutPausing() {
            // Status != OPEN, so the SLA stays paused (carried over) — no PAUSE op, just a status write.
            assertDecision(decide(obs(CHANGES_REQUESTED).approved().storedRemaining(STORED)), APPROVED, NONE);
        }

        @Test
        void noActionableReviewsResumesSlaToOpen() {
            assertDecision(decide(obs(CHANGES_REQUESTED).storedRemaining(STORED)), OPEN, RESUME);
        }

        @Test
        void noActionableReviewsWithoutStoredRemainingIsNoOp() {
            // A no-SLA record that reached CHANGES_REQUESTED has nothing to resume — stay put, no write.
            assertDecision(decide(obs(CHANGES_REQUESTED)), CHANGES_REQUESTED, NONE);
        }

        @Test
        void stillRequestingChangesIsNoOp() {
            assertDecision(
                    decide(obs(CHANGES_REQUESTED).changesRequested().storedRemaining(STORED)), CHANGES_REQUESTED, NONE);
        }

        @Test
        void mergeableWithNoVerdictIsNoOp() {
            // Isolates the approved() conjunct of the "approved + mergeable" row — a mergeable PR whose
            // team verdict has been dismissed must stay put, not be CLOSED + NOTIFY_APPROVED.
            assertDecision(decide(obs(CHANGES_REQUESTED).mergeable()), CHANGES_REQUESTED, NONE);
        }
    }

    @Nested
    class ApprovedState {

        @Test
        void mergeableCloses() {
            assertDecision(decide(obs(APPROVED).approved().mergeable()), CLOSED, SET_CLOSED_AT, NOTIFY_APPROVED);
        }

        @Test
        void mergeableClosesEvenWhenLatestVerdictRequestsChanges() {
            // Mergeability is checked before the verdict — a mergeable PR closes regardless.
            assertDecision(
                    decide(obs(APPROVED).changesRequested().mergeable()), CLOSED, SET_CLOSED_AT, NOTIFY_APPROVED);
        }

        @Test
        void changesRequestedWhileNotMergeableReRequestsChanges() {
            assertDecision(
                    decide(obs(APPROVED).changesRequested().storedRemaining(STORED)),
                    CHANGES_REQUESTED,
                    NONE,
                    NOTIFY_CHANGES_REQUESTED);
        }

        @Test
        void approvedButNotYetMergeableIsNoOp() {
            assertDecision(decide(obs(APPROVED).approved().storedRemaining(STORED)), APPROVED, NONE);
        }

        @Test
        void noVerdictAndNotMergeableIsNoOp() {
            assertDecision(decide(obs(APPROVED).storedRemaining(STORED)), APPROVED, NONE);
        }
    }

    @Nested
    class EscalatedState {

        @Test
        void approvedAndMergeableCloses() {
            assertDecision(decide(obs(ESCALATED).approved().mergeable()), CLOSED, SET_CLOSED_AT, NOTIFY_APPROVED);
        }

        @Test
        void approvedNotMergeableTransitionsToApproved() {
            assertDecision(decide(obs(ESCALATED).approved()), APPROVED, NONE);
        }

        @Test
        void changesRequestedTransitionsToChangesRequested() {
            assertDecision(
                    decide(obs(ESCALATED).changesRequested()), CHANGES_REQUESTED, NONE, NOTIFY_CHANGES_REQUESTED);
        }

        @Test
        void changesRequestedWinsOverMergeability() {
            // Asymmetric with APPROVED: in ESCALATED the verdict is evaluated before mergeability,
            // so a mergeable PR whose latest verdict requests changes goes to CHANGES_REQUESTED.
            assertDecision(
                    decide(obs(ESCALATED).changesRequested().mergeable()),
                    CHANGES_REQUESTED,
                    NONE,
                    NOTIFY_CHANGES_REQUESTED);
        }

        @Test
        void noVerdictNeverReEscalatesEvenIfMergeable() {
            // No re-escalation, and mergeability alone does not close an ESCALATED record.
            assertDecision(decide(obs(ESCALATED).mergeable()), ESCALATED, NONE);
        }
    }

    @Nested
    class CodeownerRepos {

        @Test
        void codeownerApprovedAndMergeableEntersAwaitingMerge() {
            assertDecision(
                    decide(obs(OPEN).requiresCodeowners().codeownerApproved().mergeable()),
                    AWAITING_MERGE,
                    START,
                    NOTIFY_AWAITING_MERGE);
        }

        @Test
        void teamApprovedButCodeownersPendingStaysOpen() {
            // The crux: on a codeowner repo a team approval + mergeable must NOT close the PR — only a
            // codeowner approval opens the merge gate. Without the !requiresCodeowners guard on the
            // CLOSED row this would wrongly close before the code owners reviewed.
            assertDecision(decide(obs(OPEN).requiresCodeowners().approved().mergeable()), OPEN, NONE);
        }

        @Test
        void codeownerApprovedButNotMergeableStaysOpen() {
            assertDecision(decide(obs(OPEN).requiresCodeowners().codeownerApproved()), OPEN, NONE);
        }

        @Test
        void entersAwaitingMergeFromChangesRequested() {
            assertDecision(
                    decide(obs(CHANGES_REQUESTED)
                            .requiresCodeowners()
                            .codeownerApproved()
                            .mergeable()),
                    AWAITING_MERGE,
                    START,
                    NOTIFY_AWAITING_MERGE);
        }

        @Test
        void awaitingMergeDoesNotCloseOnMergeable() {
            // The whole point of AWAITING_MERGE: mergeability alone never closes it — only the real merge.
            assertDecision(
                    decide(obs(AWAITING_MERGE)
                            .requiresCodeowners()
                            .codeownerApproved()
                            .mergeable()),
                    AWAITING_MERGE,
                    NONE);
        }

        @Test
        void awaitingMergeClosesOnRealMerge() {
            assertDecision(decide(obs(AWAITING_MERGE).merged()), CLOSED, SET_CLOSED_AT, NOTIFY_MERGED);
        }

        @Test
        void awaitingMergeBreachEscalatesToMergeEscalated() {
            assertDecision(decide(obs(AWAITING_MERGE).slaBreached()), MERGE_ESCALATED, NONE, ESCALATE_MERGE);
        }

        @Test
        void awaitingMergeChangesRequestedPausesWithLiveDeadline() {
            assertDecision(
                    decide(obs(AWAITING_MERGE).changesRequested().liveDeadline(REMAINING)),
                    CHANGES_REQUESTED,
                    new SlaOp.Pause(REMAINING),
                    NOTIFY_CHANGES_REQUESTED);
        }

        @Test
        void mergeEscalatedDoesNotCloseOnMergeable() {
            assertDecision(
                    decide(obs(MERGE_ESCALATED)
                            .requiresCodeowners()
                            .codeownerApproved()
                            .mergeable()),
                    MERGE_ESCALATED,
                    NONE);
        }

        @Test
        void mergeEscalatedClosesOnRealMerge() {
            assertDecision(decide(obs(MERGE_ESCALATED).merged()), CLOSED, SET_CLOSED_AT, NOTIFY_MERGED);
        }

        @Test
        void mergeEscalatedChangesRequestedPausesBreachedRemainingSoReApprovalReEscalates() {
            // A merge-escalated record always has a breached deadline; a code owner requesting changes pauses
            // that (clamped-to-zero) remaining rather than discarding it. On re-approval the record resumes
            // zero and re-escalates immediately, instead of being handed a fresh full merge window — so a code
            // owner can't reset the merge clock and dodge re-escalation by toggling changes-requested after a
            // breach. (Contrast the review ESCALATED → CHANGES_REQUESTED row, which uses none().)
            assertDecision(
                    decide(obs(MERGE_ESCALATED).changesRequested().slaBreached()),
                    CHANGES_REQUESTED,
                    new SlaOp.Pause(Duration.ZERO),
                    NOTIFY_CHANGES_REQUESTED);
        }

        @Test
        void activeChangesRequestedBlocksEntryToAwaitingMerge() {
            // Flap guard: code owners approved + mergeable, but a changes-requested verdict is live (e.g. a
            // non-required reviewer, which doesn't clear GitHub's reviewDecision). Changes-requested must win
            // over the merge gate, otherwise the record oscillates AWAITING_MERGE<->CHANGES_REQUESTED every poll.
            assertDecision(
                    decide(obs(OPEN)
                            .requiresCodeowners()
                            .codeownerApproved()
                            .mergeable()
                            .changesRequested()
                            .liveDeadline(REMAINING)),
                    CHANGES_REQUESTED,
                    new SlaOp.Pause(REMAINING),
                    NOTIFY_CHANGES_REQUESTED);
        }

        @Test
        void changesRequestedDoesNotBounceBackToAwaitingMergeWhileVerdictActive() {
            // The other half of the flap guard: from CHANGES_REQUESTED, an active changes-requested verdict
            // keeps the record put instead of immediately re-entering AWAITING_MERGE (which would re-notify
            // and reset the merge clock every poll).
            assertDecision(
                    decide(obs(CHANGES_REQUESTED)
                            .requiresCodeowners()
                            .codeownerApproved()
                            .mergeable()
                            .changesRequested()),
                    CHANGES_REQUESTED,
                    NONE);
        }

        @Test
        void codeownerRepoDoesNotReviewEscalateInOpen() {
            // Clock held in OPEN for code-owner repos is structural: even if a live deadline leaked in and
            // breached with no verdict, OPEN must not review-escalate (that is the merge phase's job).
            assertDecision(decide(obs(OPEN).requiresCodeowners().slaBreached()), OPEN, NONE);
        }
    }

    // ── Helpers ──

    private static Decision decide(ObsBuilder b) {
        return PrLifecycle.decide(b.build());
    }

    private static void assertDecision(Decision d, PrTrackingStatus next, SlaOp slaOp, Effect... effects) {
        assertThat(d.next()).isEqualTo(next);
        assertThat(d.slaOp()).isEqualTo(slaOp);
        assertThat(d.effects()).containsExactly(effects);
    }

    private static ObsBuilder obs(PrTrackingStatus current) {
        return new ObsBuilder(current);
    }

    /** Builds an {@link Observation} with all-false / null defaults and only the relevant tweaks set. */
    private static final class ObsBuilder {
        private final PrTrackingStatus current;
        private @Nullable Verdict verdict;
        private boolean mergeable;
        private boolean merged;
        private boolean closed;
        private boolean slaBreached;
        private boolean hasLiveDeadline;
        private @Nullable Duration remainingForPause;
        private @Nullable Duration slaRemainingStored;
        private boolean requiresCodeowners;
        private boolean codeownerApproved;

        private ObsBuilder(PrTrackingStatus current) {
            this.current = current;
        }

        ObsBuilder approved() {
            this.verdict = Verdict.APPROVED;
            return this;
        }

        ObsBuilder changesRequested() {
            this.verdict = Verdict.CHANGES_REQUESTED;
            return this;
        }

        ObsBuilder mergeable() {
            this.mergeable = true;
            return this;
        }

        ObsBuilder merged() {
            this.merged = true;
            this.closed = true;
            return this;
        }

        ObsBuilder closed() {
            this.closed = true;
            return this;
        }

        ObsBuilder slaBreached() {
            this.hasLiveDeadline = true;
            this.remainingForPause = Duration.ZERO;
            this.slaBreached = true;
            return this;
        }

        ObsBuilder liveDeadline(Duration remaining) {
            this.hasLiveDeadline = true;
            this.remainingForPause = remaining;
            return this;
        }

        ObsBuilder storedRemaining(Duration stored) {
            this.slaRemainingStored = stored;
            return this;
        }

        ObsBuilder requiresCodeowners() {
            this.requiresCodeowners = true;
            return this;
        }

        ObsBuilder codeownerApproved() {
            this.codeownerApproved = true;
            return this;
        }

        Observation build() {
            return new Observation(
                    current,
                    verdict,
                    mergeable,
                    merged,
                    closed,
                    slaBreached,
                    hasLiveDeadline,
                    remainingForPause,
                    slaRemainingStored,
                    requiresCodeowners,
                    codeownerApproved);
        }
    }
}
