package com.coreeng.supportbot.prtracking;

import static com.coreeng.supportbot.dbschema.enums.PrTrackingStatus.APPROVED;
import static com.coreeng.supportbot.dbschema.enums.PrTrackingStatus.CHANGES_REQUESTED;
import static com.coreeng.supportbot.dbschema.enums.PrTrackingStatus.CLOSED;
import static com.coreeng.supportbot.dbschema.enums.PrTrackingStatus.ESCALATED;
import static com.coreeng.supportbot.dbschema.enums.PrTrackingStatus.OPEN;
import static com.coreeng.supportbot.prtracking.PrLifecycle.Effect.ESCALATE;
import static com.coreeng.supportbot.prtracking.PrLifecycle.Effect.NOTIFY_APPROVED;
import static com.coreeng.supportbot.prtracking.PrLifecycle.Effect.NOTIFY_CHANGES_REQUESTED;
import static com.coreeng.supportbot.prtracking.PrLifecycle.Effect.NOTIFY_CLOSED;
import static com.coreeng.supportbot.prtracking.PrLifecycle.Effect.NOTIFY_MERGED;
import static com.coreeng.supportbot.prtracking.PrLifecycle.SlaOp.NONE;
import static com.coreeng.supportbot.prtracking.PrLifecycle.SlaOp.RESUME;
import static com.coreeng.supportbot.prtracking.PrLifecycle.SlaOp.SET_CLOSED_AT;
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
                    slaRemainingStored);
        }
    }
}
