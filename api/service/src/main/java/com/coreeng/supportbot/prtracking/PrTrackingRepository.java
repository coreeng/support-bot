package com.coreeng.supportbot.prtracking;

import com.coreeng.supportbot.dbschema.enums.PrTrackingStatus;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

public interface PrTrackingRepository {

    PrTrackingRecord insert(NewPrTracking newRecord);

    List<PrTrackingRecord> findAllByStatus(PrTrackingStatus status);

    /** Returns all records with status OPEN or ESCALATED. */
    List<PrTrackingRecord> findAllActive();

    PrTrackingRecord updateStatus(
            long id, PrTrackingStatus newStatus, @Nullable Instant closedAt, @Nullable Long escalationId);

    /** Returns true if any OPEN or ESCALATED record still exists for this ticket. */
    boolean hasAnyActiveForTicket(long ticketId);

    boolean existsByTicketIdAndRepoAndPrNumber(long ticketId, String githubRepo, int prNumber);
}
