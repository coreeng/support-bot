package com.coreeng.supportbot.prtracking;

import com.coreeng.supportbot.dbschema.enums.PrTrackingStatus;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record PrTrackingRecord(
        long id,
        long ticketId,
        String githubRepo,
        int prNumber,
        Instant prCreatedAt,
        Instant slaDeadline,
        String owningTeam,
        PrTrackingStatus status,
        @Nullable Long escalationId,
        @Nullable Instant closedAt) {}
