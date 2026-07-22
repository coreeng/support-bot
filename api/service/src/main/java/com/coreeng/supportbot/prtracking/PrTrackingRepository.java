package com.coreeng.supportbot.prtracking;

import com.coreeng.supportbot.dbschema.enums.PrTrackingStatus;
import com.coreeng.supportbot.prtracking.source.Provider;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

public interface PrTrackingRepository {

    @Nullable PrTrackingRecord insertIfAbsent(NewPrTracking newRecord);

    @Nullable PrTrackingRecord findById(long id);

    List<PrTrackingRecord> findAllByStatus(PrTrackingStatus status);

    /** Returns all records in a non-terminal status (OPEN, ESCALATED, CHANGES_REQUESTED, APPROVED, AWAITING_MERGE, MERGE_ESCALATED). */
    List<PrTrackingRecord> findAllActive();

    PrTrackingRecord updateStatus(
            long id, PrTrackingStatus newStatus, @Nullable Instant closedAt, @Nullable Long escalationId);

    /** Pauses the SLA clock: sets status to newStatus, stores the remaining duration, and nulls the deadline. */
    PrTrackingRecord pauseSla(long id, PrTrackingStatus newStatus, Duration remaining);

    /** Resumes the SLA clock with a new deadline, nulling the remaining duration and setting status to OPEN. */
    PrTrackingRecord resumeSla(long id, Instant newDeadline);

    /**
     * Starts the SLA clock with a fresh deadline and an explicit status, nulling any stored remaining.
     * Test-seeding helper only ({@code PrTrackingTestController}) — production entry into the merge phase
     * uses {@link #enterMergePhase} instead, which also latches {@code merge_phase_entered}.
     */
    PrTrackingRecord startSla(long id, PrTrackingStatus newStatus, Instant newDeadline);

    /**
     * Enters (or re-enters) the merge phase: sets status + deadline, clears any stored remaining, and
     * latches {@code merge_phase_entered = true} — all in one write, so a crash or overlapping poll can
     * never see {@code AWAITING_MERGE} with the flag still false.
     */
    PrTrackingRecord enterMergePhase(long id, PrTrackingStatus newStatus, @Nullable Instant newDeadline);

    /**
     * Returns true if any non-terminal record (OPEN, ESCALATED, CHANGES_REQUESTED, APPROVED, AWAITING_MERGE,
     * MERGE_ESCALATED) that can auto-close the ticket still exists for this ticket.
     */
    boolean hasAnyActiveClosableForTicket(long ticketId);

    /** Updates activity timestamps on a tracking record. */
    void updateActivityTimestamps(long id, @Nullable Instant lastReviewAt, @Nullable Instant lastAuthorActivityAt);

    /**
     * Sticky flag: marks that a provider has reported a genuinely pending code-owner review request
     * for this record at least once (see {@code PrLifecyclePoller#codeownerApproved}). Never unset.
     * Calling this when the flag is already {@code true} is harmless and idempotent (just rewrites
     * the same value) — callers don't need to guard the call, though existing callers do so anyway
     * to avoid a redundant write.
     */
    PrTrackingRecord markCodeownerReviewRequested(long id);

    boolean existsByTicketIdAndRepoAndPrNumber(long ticketId, Provider provider, String repo, int prNumber);

    /** Returns all non-terminal (OPEN, ESCALATED, CHANGES_REQUESTED, APPROVED, AWAITING_MERGE, MERGE_ESCALATED) PR tracking records, optionally filtered by owning team. */
    List<InFlightPr> findAllInFlight(@Nullable String owningTeam);

    /** Stats per repo for PRs created within the given date range. */
    List<RepoInsights> getInsightsByRepo(@Nullable LocalDate dateFrom, @Nullable LocalDate dateTo);

    /**
     * Support-request funnel for tickets created within the given date range: total support
     * tickets, how many had a PR, and how many of those PR tickets needed manual intervention.
     * Anchored on ticket creation (the {@code query} table's {@code date} column) so the PR and
     * intervention counts are true subsets of the total.
     */
    RequestBreakdown getRequestBreakdown(@Nullable LocalDate dateFrom, @Nullable LocalDate dateTo);
}
