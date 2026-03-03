package com.coreeng.supportbot.prtracking;

import static java.util.Objects.requireNonNull;

import java.time.Instant;

public record NewPrTracking(
        long ticketId,
        String githubRepo,
        int prNumber,
        Instant prCreatedAt,
        Instant slaDeadline,
        String owningTeam,
        boolean closeTicketOnResolve) {
    public NewPrTracking {
        requireNonNull(githubRepo, "githubRepo must not be null");
        requireNonNull(prCreatedAt, "prCreatedAt must not be null");
        requireNonNull(slaDeadline, "slaDeadline must not be null");
        requireNonNull(owningTeam, "owningTeam must not be null");
    }

    public NewPrTracking(
            long ticketId,
            String githubRepo,
            int prNumber,
            Instant prCreatedAt,
            Instant slaDeadline,
            String owningTeam) {
        this(ticketId, githubRepo, prNumber, prCreatedAt, slaDeadline, owningTeam, true);
    }
}
