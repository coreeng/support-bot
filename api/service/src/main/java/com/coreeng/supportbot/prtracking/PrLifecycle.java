package com.coreeng.supportbot.prtracking;

import static com.coreeng.supportbot.dbschema.enums.PrTrackingStatus.APPROVED;
import static com.coreeng.supportbot.dbschema.enums.PrTrackingStatus.AWAITING_MERGE;
import static com.coreeng.supportbot.dbschema.enums.PrTrackingStatus.CHANGES_REQUESTED;
import static com.coreeng.supportbot.dbschema.enums.PrTrackingStatus.CLOSED;
import static com.coreeng.supportbot.dbschema.enums.PrTrackingStatus.ESCALATED;
import static com.coreeng.supportbot.dbschema.enums.PrTrackingStatus.MERGE_ESCALATED;
import static com.coreeng.supportbot.dbschema.enums.PrTrackingStatus.OPEN;
import static java.util.Objects.requireNonNull;

import com.coreeng.supportbot.dbschema.enums.PrTrackingStatus;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * The PR-tracking lifecycle FSM, expressed as a <b>declarative transition table</b> plus a pure
 * {@link #decide(Observation)} function (spike §6.3, functional core / imperative shell).
 *
 * <p>The poller is a reconciler, not an event stream: on each poll it {@code observe()}s the world
 * into an {@link Observation} (the only impure, {@code now()}-based step), calls {@link #decide} to
 * pick the next state + ordered effects, then {@code apply()}s them. {@code decide} touches no
 * clients, so it is trivially unit-testable without mocks, and {@link #TRANSITIONS} is the single
 * source of truth for legal transitions — read by both {@code decide} and {@link MermaidRenderer}.
 *
 * <p>Behaviour here is a faithful port of the previous implicit {@code switch(status)} +
 * {@code if/else} chains in {@link PrLifecyclePoller}; adding a state (C's {@code AWAITING_MERGE},
 * B's {@code AWAITING_APPROVALS}) becomes a localised change — new rows, not edits to four chains.
 */
public final class PrLifecycle {

    private PrLifecycle() {}

    /** The single latest actionable team review, reduced to its verdict. {@code null} = none. */
    public enum Verdict {
        APPROVED,
        CHANGES_REQUESTED
    }

    /**
     * Pure, already-resolved inputs to {@link #decide} — no clients, no {@code now()}. The
     * {@code now()}-derived fields ({@code slaBreached}, {@code remainingForPause}) are computed once
     * in {@code observe()}. {@code remainingForPause} is non-null exactly when {@code hasLiveDeadline}.
     *
     * <p>(B later adds {@code requiredApprovalsSatisfied}.)
     */
    public record Observation(
            PrTrackingStatus current,
            @Nullable Verdict latestVerdict,
            boolean mergeable,
            boolean merged,
            boolean closed,
            boolean slaBreached,
            boolean hasLiveDeadline,
            @Nullable Duration remainingForPause,
            @Nullable Duration slaRemainingStored,
            boolean requiresCodeowners,
            boolean codeownerApproved) {

        boolean approved() {
            return latestVerdict == Verdict.APPROVED;
        }

        boolean changesRequested() {
            return latestVerdict == Verdict.CHANGES_REQUESTED;
        }

        boolean noVerdict() {
            return latestVerdict == null;
        }

        /**
         * True when a requires-codeowners repo has its code owners' approval and the PR is mergeable, with
         * no outstanding changes-requested verdict. For code-owner repos {@code changesRequested()} is
         * derived from the provider's *aggregate* code-owner decision (GitHub {@code reviewDecision ==
         * CHANGES_REQUESTED}) rather than raw reviews, so it reflects a code owner actually requesting
         * changes — not a drive-by. The {@code !changesRequested()} guard matters because such a verdict can
         * coexist with {@code codeownerApproved}/{@code mergeable} being momentarily stale: without it the
         * record would flap AWAITING_MERGE ↔ CHANGES_REQUESTED, posting two contradictory notifications each
         * poll. When the changes-requested clears, re-entry resumes the paused merge clock rather than
         * restarting it (see {@code PrLifecyclePoller#startMergeClock}), so the detour doesn't reset the
         * merge SLA. Changes-requested takes priority until it clears, mirroring the OPEN rows.
         */
        boolean readyForCodeownerMerge() {
            return requiresCodeowners && codeownerApproved && mergeable && !changesRequested();
        }
    }

    /**
     * What to do to the SLA columns / status row. {@code Pause} carries the clamped remaining computed
     * in {@code observe()}; {@code Resume}, {@code Start} and {@code SetClosedAt} are markers — the
     * shell stamps the {@code now()}-based deadline / closedAt at write time, exactly as the old code did.
     */
    public sealed interface SlaOp {
        SlaOp NONE = new None();
        SlaOp RESUME = new Resume();
        SlaOp START = new Start();
        SlaOp SET_CLOSED_AT = new SetClosedAt();

        record None() implements SlaOp {}

        record Pause(Duration remaining) implements SlaOp {}

        record Resume() implements SlaOp {}

        /**
         * Starts (or resumes) the SLA clock on entry to a state (AWAITING_MERGE): the shell stamps
         * {@code now} + the paused merge remaining if a changes-requested detour left one, else
         * {@code now} + the repo's configured SLA. A no-op when the repo has no SLA and nothing was
         * paused, so such repos never merge-escalate. See {@code PrLifecyclePoller#startMergeClock}.
         */
        record Start() implements SlaOp {}

        record SetClosedAt() implements SlaOp {}
    }

    /**
     * A side effect to run in {@link Decision#effects()} list order. {@code NotifyApproved} is the
     * approval-closure message; {@code NotifyClosed} carries {@link MessageEvent#MERGED} or
     * {@link MessageEvent#CLOSED}. {@code Escalate} and {@code EscalateMerge} are compound (each owns its
     * own status write — see {@code PrLifecyclePoller}).
     */
    public sealed interface Effect {
        Effect NOTIFY_CHANGES_REQUESTED = new NotifyChangesRequested();
        Effect NOTIFY_APPROVED = new NotifyApproved();
        Effect NOTIFY_AWAITING_MERGE = new NotifyAwaitingMerge();
        Effect NOTIFY_MERGED = new NotifyClosed(MessageEvent.MERGED);
        Effect NOTIFY_CLOSED = new NotifyClosed(MessageEvent.CLOSED);
        Effect ESCALATE = new Escalate();
        Effect ESCALATE_MERGE = new EscalateMerge();

        record NotifyChangesRequested() implements Effect {}

        record NotifyApproved() implements Effect {}

        /** Notifies the tenant that the code owners have approved and the maintaining team can now merge. */
        record NotifyAwaitingMerge() implements Effect {}

        record NotifyClosed(MessageEvent event) implements Effect {}

        record Escalate() implements Effect {}

        /** Like {@link Escalate} but chases the maintaining team to merge; owns its MERGE_ESCALATED write. */
        record EscalateMerge() implements Effect {}
    }

    /** Pure output: next state + SLA op + ordered effects. Nothing is executed here. */
    public record Decision(PrTrackingStatus next, SlaOp slaOp, List<Effect> effects) {
        public Decision {
            requireNonNull(next, "next");
            requireNonNull(slaOp, "slaOp");
            effects = List.copyOf(effects);
        }
    }

    /**
     * One row of the declarative table = one edge of the diagram. {@code from == null} means "any
     * active state". Rows are evaluated in order; the first whose {@code from} matches and whose
     * {@code guard} holds wins. {@code label} documents the guard and is what {@link MermaidRenderer}
     * renders.
     */
    record Transition(
            @Nullable PrTrackingStatus from,
            PrTrackingStatus to,
            String label,
            Predicate<Observation> guard,
            Function<Observation, SlaOp> slaOp,
            List<Effect> effects) {}

    /**
     * The transition table — ordered; first match wins. Order encodes three "checked-first" facts
     * from the old code: closed/merged beats everything; in {@code APPROVED}, mergeability is checked
     * before the verdict; in {@code ESCALATED}, re-approval is checked before mergeability (the
     * asymmetry is deliberate and preserved).
     */
    static final List<Transition> TRANSITIONS = List.of(
            // Any active state: provider reports the PR closed/merged → terminal CLOSED.
            new Transition(
                    null,
                    CLOSED,
                    "PR merged",
                    o -> o.closed() && o.merged(),
                    PrLifecycle::setClosedAt,
                    List.of(Effect.NOTIFY_MERGED)),
            new Transition(
                    null,
                    CLOSED,
                    "PR closed",
                    o -> o.closed() && !o.merged(),
                    PrLifecycle::setClosedAt,
                    List.of(Effect.NOTIFY_CLOSED)),

            // requires-codeowners repos: once the code owners have approved and the PR is mergeable, hand
            // off to AWAITING_MERGE to chase the maintaining team to merge. Listed before the per-state
            // "approved + mergeable → CLOSED" rows (themselves guarded with !requiresCodeowners) so a
            // codeowner repo never closes on mergeability — only on the real merge (the rows above). One
            // row per source state (rather than a from==null wildcard) so the generated diagram shows the
            // real edges and AWAITING_MERGE/MERGE_ESCALATED are never spurious self-sources.
            new Transition(
                    OPEN,
                    AWAITING_MERGE,
                    "codeowner-approved + mergeable",
                    Observation::readyForCodeownerMerge,
                    PrLifecycle::start,
                    List.of(Effect.NOTIFY_AWAITING_MERGE)),
            new Transition(
                    CHANGES_REQUESTED,
                    AWAITING_MERGE,
                    "codeowner-approved + mergeable",
                    Observation::readyForCodeownerMerge,
                    PrLifecycle::start,
                    List.of(Effect.NOTIFY_AWAITING_MERGE)),
            new Transition(
                    APPROVED,
                    AWAITING_MERGE,
                    "codeowner-approved + mergeable",
                    Observation::readyForCodeownerMerge,
                    PrLifecycle::start,
                    List.of(Effect.NOTIFY_AWAITING_MERGE)),
            new Transition(
                    ESCALATED,
                    AWAITING_MERGE,
                    "codeowner-approved + mergeable",
                    Observation::readyForCodeownerMerge,
                    PrLifecycle::start,
                    List.of(Effect.NOTIFY_AWAITING_MERGE)),

            // OPEN
            new Transition(
                    OPEN,
                    CHANGES_REQUESTED,
                    "changes requested, live deadline",
                    o -> o.changesRequested() && o.hasLiveDeadline(),
                    PrLifecycle::pause,
                    List.of(Effect.NOTIFY_CHANGES_REQUESTED)),
            new Transition(
                    OPEN,
                    CHANGES_REQUESTED,
                    "changes requested, no-SLA record",
                    o -> o.changesRequested() && !o.hasLiveDeadline() && o.slaRemainingStored() == null,
                    PrLifecycle::none,
                    List.of(Effect.NOTIFY_CHANGES_REQUESTED)),
            new Transition(
                    OPEN,
                    OPEN,
                    "changes requested, SLA paused (notify only)",
                    o -> o.changesRequested() && !o.hasLiveDeadline() && o.slaRemainingStored() != null,
                    PrLifecycle::none,
                    List.of(Effect.NOTIFY_CHANGES_REQUESTED)),
            new Transition(
                    OPEN,
                    CLOSED,
                    "approved + mergeable",
                    o -> o.approved() && o.mergeable() && !o.requiresCodeowners(),
                    PrLifecycle::setClosedAt,
                    List.of(Effect.NOTIFY_APPROVED)),
            new Transition(
                    OPEN,
                    APPROVED,
                    "approved, not mergeable, live deadline",
                    o -> o.approved() && !o.mergeable() && o.hasLiveDeadline(),
                    PrLifecycle::pause,
                    List.of()),
            new Transition(
                    OPEN,
                    APPROVED,
                    "approved, not mergeable, no-SLA",
                    o -> o.approved() && !o.mergeable() && !o.hasLiveDeadline(),
                    PrLifecycle::none,
                    List.of()),
            // The !requiresCodeowners guard makes "clock held in OPEN for code-owner repos" structural: such
            // a repo must never review-escalate in OPEN, even if a live deadline leaked in (e.g. a merge
            // clock paused on a changes-requested detour and later resumed via CHANGES_REQUESTED → OPEN).
            //
            // This is also the only row that ever sets ESCALATED, and requiresCodeowners is fixed per repo
            // (never toggles per-PR) — so together with readyForCodeownerMerge() requiring requiresCodeowners
            // to reach AWAITING_MERGE/MERGE_ESCALATED, a single PR can never pass through both ESCALATED and
            // MERGE_ESCALATED. Don't relax this guard without first handling escalate()/escalateMerge()
            // sharing one Escalation row per ticket (escalation_thread_ts_unique) — today that's a non-issue
            // only because the two effects are mutually exclusive per repo.
            new Transition(
                    OPEN,
                    ESCALATED,
                    "no verdict + SLA breached",
                    o -> o.noVerdict() && o.slaBreached() && !o.requiresCodeowners(),
                    PrLifecycle::none,
                    List.of(Effect.ESCALATE)),

            // CHANGES_REQUESTED
            new Transition(
                    CHANGES_REQUESTED,
                    CLOSED,
                    "approved + mergeable",
                    o -> o.approved() && o.mergeable() && !o.requiresCodeowners(),
                    PrLifecycle::setClosedAt,
                    List.of(Effect.NOTIFY_APPROVED)),
            new Transition(
                    CHANGES_REQUESTED,
                    APPROVED,
                    "approved, not mergeable",
                    o -> o.approved() && !o.mergeable(),
                    PrLifecycle::none,
                    List.of()),
            new Transition(
                    CHANGES_REQUESTED,
                    OPEN,
                    "no actionable reviews remain",
                    o -> o.noVerdict() && o.slaRemainingStored() != null,
                    PrLifecycle::resume,
                    List.of()),

            // APPROVED — mergeable checked first (any verdict)
            new Transition(
                    APPROVED,
                    CLOSED,
                    "mergeable",
                    o -> o.mergeable() && !o.requiresCodeowners(),
                    PrLifecycle::setClosedAt,
                    List.of(Effect.NOTIFY_APPROVED)),
            new Transition(
                    APPROVED,
                    CHANGES_REQUESTED,
                    "changes requested, not mergeable",
                    o -> !o.mergeable() && o.changesRequested(),
                    PrLifecycle::none,
                    List.of(Effect.NOTIFY_CHANGES_REQUESTED)),

            // ESCALATED — re-approval evaluated before mergeability (asymmetric with APPROVED)
            new Transition(
                    ESCALATED,
                    CLOSED,
                    "approved + mergeable",
                    o -> o.approved() && o.mergeable() && !o.requiresCodeowners(),
                    PrLifecycle::setClosedAt,
                    List.of(Effect.NOTIFY_APPROVED)),
            new Transition(
                    ESCALATED,
                    APPROVED,
                    "approved, not mergeable",
                    o -> o.approved() && !o.mergeable(),
                    PrLifecycle::none,
                    List.of()),
            new Transition(
                    ESCALATED,
                    CHANGES_REQUESTED,
                    "changes requested",
                    Observation::changesRequested,
                    PrLifecycle::none,
                    List.of(Effect.NOTIFY_CHANGES_REQUESTED)),

            // AWAITING_MERGE (requires-codeowners) — code owners approved + mergeable; chasing the
            // maintaining team to merge. Closes only on the real merge (the "PR merged" row above);
            // there is deliberately no mergeable → CLOSED edge. Changes-requested takes priority over a
            // merge-SLA breach, mirroring OPEN.
            new Transition(
                    AWAITING_MERGE,
                    CHANGES_REQUESTED,
                    "changes requested, live deadline",
                    o -> o.changesRequested() && o.hasLiveDeadline(),
                    PrLifecycle::pause,
                    List.of(Effect.NOTIFY_CHANGES_REQUESTED)),
            new Transition(
                    AWAITING_MERGE,
                    CHANGES_REQUESTED,
                    "changes requested, no live deadline",
                    o -> o.changesRequested() && !o.hasLiveDeadline(),
                    PrLifecycle::none,
                    List.of(Effect.NOTIFY_CHANGES_REQUESTED)),
            new Transition(
                    AWAITING_MERGE,
                    MERGE_ESCALATED,
                    "merge SLA breached",
                    Observation::slaBreached,
                    PrLifecycle::none,
                    List.of(Effect.ESCALATE_MERGE)),

            // MERGE_ESCALATED — mirrors ESCALATED but merge-aware: no approved+mergeable → CLOSED exit,
            // so it too closes only on the real merge.
            new Transition(
                    MERGE_ESCALATED,
                    CHANGES_REQUESTED,
                    "changes requested after escalation",
                    Observation::changesRequested,
                    PrLifecycle::none,
                    List.of(Effect.NOTIFY_CHANGES_REQUESTED)));

    /** Pure, table-driven. First matching row wins; otherwise stay put with no write. */
    public static Decision decide(Observation o) {
        for (Transition t : TRANSITIONS) {
            if ((t.from() == null || t.from() == o.current()) && t.guard().test(o)) {
                return new Decision(t.to(), t.slaOp().apply(o), t.effects());
            }
        }
        return new Decision(o.current(), SlaOp.NONE, List.of());
    }

    private static SlaOp none(Observation o) {
        return SlaOp.NONE;
    }

    private static SlaOp resume(Observation o) {
        return SlaOp.RESUME;
    }

    private static SlaOp setClosedAt(Observation o) {
        return SlaOp.SET_CLOSED_AT;
    }

    private static SlaOp pause(Observation o) {
        return new SlaOp.Pause(requireNonNull(o.remainingForPause(), "remainingForPause must be set to pause"));
    }

    private static SlaOp start(Observation o) {
        return SlaOp.START;
    }
}
