package com.coreeng.supportbot.prtracking;

import com.coreeng.supportbot.dbschema.enums.PrTrackingStatus;
import java.util.List;

public interface PrTrackingRepository {

    PrTrackingRecord insert(NewPrTracking newRecord);

    List<PrTrackingRecord> findAllByStatus(PrTrackingStatus status);

    boolean existsByTicketIdAndRepoAndPrNumber(long ticketId, String githubRepo, int prNumber);
}
