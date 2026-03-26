package com.coreeng.supportbot.prtracking;

import com.coreeng.supportbot.dbschema.enums.PrTrackingStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

public interface PrTrackingRepository {

    @Nullable PrTrackingRecord insertIfAbsent(NewPrTracking newRecord);

    List<PrTrackingRecord> findAllByStatus(PrTrackingStatus status);

    /** Returns all records with status OPEN, ESCALATED, CHANGES_REQUESTED, or APPROVED. */
    List<PrTrackingRecord> findAllActive();

    PrTrackingRecord updateStatus(
            long id, PrTrackingStatus newStatus, @Nullable Instant closedAt, @Nullable Long escalationId);

    /** Returns true if any OPEN, ESCALATED, CHANGES_REQUESTED, or APPROVED record still exists for this ticket. */
    boolean hasAnyActiveForTicket(long ticketId);

    /**
     * Returns true if any OPEN, ESCALATED, CHANGES_REQUESTED, or APPROVED record that can auto-close ticket still
     * exists for this ticket.
     */
    boolean hasAnyActiveClosableForTicket(long ticketId);

    boolean existsByTicketIdAndRepoAndPrNumber(long ticketId, String githubRepo, int prNumber);

    /** Stats per repo for PRs created within the given date range. */
    List<RepoInsights> getInsightsByRepo(@Nullable LocalDate dateFrom, @Nullable LocalDate dateTo);

    /** How many PR tickets were escalated by bot vs manually, within the given date range. */
    EscalationBreakdown getEscalationBreakdown(@Nullable LocalDate dateFrom, @Nullable LocalDate dateTo);
}
