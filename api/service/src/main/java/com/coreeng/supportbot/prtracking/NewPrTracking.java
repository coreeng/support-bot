package com.coreeng.supportbot.prtracking;

import static java.util.Objects.requireNonNull;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record NewPrTracking(
        long ticketId,
        String githubRepo,
        int prNumber,
        Instant prCreatedAt,
        @Nullable Instant slaDeadline,
        String owningTeam,
        boolean canAutoCloseTicket) {
    public NewPrTracking {
        requireNonNull(githubRepo, "githubRepo must not be null");
        requireNonNull(prCreatedAt, "prCreatedAt must not be null");
        requireNonNull(owningTeam, "owningTeam must not be null");
    }

    public NewPrTracking(
            long ticketId,
            String githubRepo,
            int prNumber,
            Instant prCreatedAt,
            @Nullable Instant slaDeadline,
            String owningTeam) {
        this(ticketId, githubRepo, prNumber, prCreatedAt, slaDeadline, owningTeam, true);
    }
}
