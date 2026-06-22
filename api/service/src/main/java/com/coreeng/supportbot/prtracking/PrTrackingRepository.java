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

    /** Returns all records with status OPEN, ESCALATED, CHANGES_REQUESTED, or APPROVED. */
    List<PrTrackingRecord> findAllActive();

    PrTrackingRecord updateStatus(
            long id, PrTrackingStatus newStatus, @Nullable Instant closedAt, @Nullable Long escalationId);

    /** Pauses the SLA clock: sets status to newStatus, stores the remaining duration, and nulls the deadline. */
    PrTrackingRecord pauseSla(long id, PrTrackingStatus newStatus, Duration remaining);

    /** Resumes the SLA clock with a new deadline, nulling the remaining duration and setting status to OPEN. */
    PrTrackingRecord resumeSla(long id, Instant newDeadline);

    /**
     * Returns true if any OPEN, ESCALATED, CHANGES_REQUESTED, or APPROVED record that can auto-close ticket still
     * exists for this ticket.
     */
    boolean hasAnyActiveClosableForTicket(long ticketId);

    /** Updates activity timestamps on a tracking record. */
    void updateActivityTimestamps(long id, @Nullable Instant lastReviewAt, @Nullable Instant lastAuthorActivityAt);

    boolean existsByTicketIdAndRepoAndPrNumber(long ticketId, Provider provider, String repo, int prNumber);

    /** Returns all active (OPEN, ESCALATED, CHANGES_REQUESTED, APPROVED) PR tracking records, optionally filtered by owning team. */
    List<InFlightPr> findAllInFlight(@Nullable String owningTeam);

    /** Stats per repo for PRs created within the given date range. */
    List<RepoInsights> getInsightsByRepo(@Nullable LocalDate dateFrom, @Nullable LocalDate dateTo);

    /** How many PR tickets were escalated by bot vs manually, within the given date range. */
    EscalationBreakdown getEscalationBreakdown(@Nullable LocalDate dateFrom, @Nullable LocalDate dateTo);

    /**
     * Support-request funnel for tickets created within the given date range: total support
     * tickets, how many had a PR, and how many of those PR tickets needed manual intervention.
     * Anchored on ticket creation (the {@code query} table's {@code date} column) so the PR and
     * intervention counts are true subsets of the total.
     */
    RequestBreakdown getRequestBreakdown(@Nullable LocalDate dateFrom, @Nullable LocalDate dateTo);
}
