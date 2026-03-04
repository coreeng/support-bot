package com.coreeng.supportbot.prtracking;

import static java.util.Objects.requireNonNull;

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
        boolean canAutoCloseTicket,
        PrTrackingStatus status,
        @Nullable Long escalationId,
        @Nullable Instant closedAt) {
    public PrTrackingRecord {
        requireNonNull(githubRepo, "githubRepo must not be null");
        requireNonNull(prCreatedAt, "prCreatedAt must not be null");
        requireNonNull(slaDeadline, "slaDeadline must not be null");
        requireNonNull(owningTeam, "owningTeam must not be null");
        requireNonNull(status, "status must not be null");
    }
}
