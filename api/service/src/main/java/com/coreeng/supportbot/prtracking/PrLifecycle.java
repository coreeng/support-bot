package com.coreeng.supportbot.prtracking;

import static com.coreeng.supportbot.dbschema.enums.PrTrackingStatus.APPROVED;
import static com.coreeng.supportbot.dbschema.enums.PrTrackingStatus.CHANGES_REQUESTED;
import static com.coreeng.supportbot.dbschema.enums.PrTrackingStatus.CLOSED;
import static com.coreeng.supportbot.dbschema.enums.PrTrackingStatus.ESCALATED;
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
     * <p>(C later adds {@code codeownerApproved}; B adds {@code requiredApprovalsSatisfied}.)
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
            @Nullable Duration slaRemainingStored) {

        boolean approved() {
            return latestVerdict == Verdict.APPROVED;
        }

        boolean changesRequested() {
            return latestVerdict == Verdict.CHANGES_REQUESTED;
        }

        boolean noVerdict() {
            return latestVerdict == null;
        }
    }

    /**
     * What to do to the SLA columns / status row. {@code Pause} carries the clamped remaining computed
     * in {@code observe()}; {@code Resume} and {@code SetClosedAt} are markers — the shell stamps the
     * {@code now()}-based deadline / closedAt at write time, exactly as the old code did.
     */
    public sealed interface SlaOp {
        SlaOp NONE = new None();
        SlaOp RESUME = new Resume();
        SlaOp SET_CLOSED_AT = new SetClosedAt();

        record None() implements SlaOp {}

        record Pause(Duration remaining) implements SlaOp {}

        record Resume() implements SlaOp {}

        record SetClosedAt() implements SlaOp {}
    }

    /**
     * A side effect to run in {@link Decision#effects()} list order. {@code NotifyApproved} is the
     * approval-closure message; {@code NotifyClosed} carries {@link MessageEvent#MERGED} or
     * {@link MessageEvent#CLOSED}. {@code Escalate} is compound (it owns its own status write — see
     * {@code PrLifecyclePoller}). (C later adds {@code NotifyAwaitingMerge}.)
     */
    public sealed interface Effect {
        Effect NOTIFY_CHANGES_REQUESTED = new NotifyChangesRequested();
        Effect NOTIFY_APPROVED = new NotifyApproved();
        Effect NOTIFY_MERGED = new NotifyClosed(MessageEvent.MERGED);
        Effect NOTIFY_CLOSED = new NotifyClosed(MessageEvent.CLOSED);
        Effect ESCALATE = new Escalate();

        record NotifyChangesRequested() implements Effect {}

        record NotifyApproved() implements Effect {}

        record NotifyClosed(MessageEvent event) implements Effect {}

        record Escalate() implements Effect {}
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
                    o -> o.approved() && o.mergeable(),
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
            new Transition(
                    OPEN,
                    ESCALATED,
                    "no verdict + SLA breached",
                    o -> o.noVerdict() && o.slaBreached(),
                    PrLifecycle::none,
                    List.of(Effect.ESCALATE)),

            // CHANGES_REQUESTED
            new Transition(
                    CHANGES_REQUESTED,
                    CLOSED,
                    "approved + mergeable",
                    o -> o.approved() && o.mergeable(),
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
                    Observation::mergeable,
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
                    o -> o.approved() && o.mergeable(),
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
}
