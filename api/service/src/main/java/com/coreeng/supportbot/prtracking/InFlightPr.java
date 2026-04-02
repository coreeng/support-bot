package com.coreeng.supportbot.prtracking;

import static java.util.Objects.requireNonNull;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record InFlightPr(
        String githubRepo,
        int prNumber,
        String prUrl,
        String status,
        String waitingOn,
        Instant prCreatedAt,
        @Nullable Instant slaDeadline,
        @Nullable Long slaRemainingSeconds,
        @Nullable Instant lastReviewAt,
        String owningTeam,
        String ticketChannelId,
        String ticketQueryTs,
        @Nullable Instant escalatedAt) {
    public InFlightPr {
        requireNonNull(githubRepo, "githubRepo must not be null");
        if (prNumber <= 0) {
            throw new IllegalArgumentException("prNumber must be positive, was " + prNumber);
        }
        requireNonNull(prUrl, "prUrl must not be null");
        requireNonNull(status, "status must not be null");
        requireNonNull(waitingOn, "waitingOn must not be null");
        requireNonNull(prCreatedAt, "prCreatedAt must not be null");
        requireNonNull(owningTeam, "owningTeam must not be null");
        requireNonNull(ticketChannelId, "ticketChannelId must not be null");
        requireNonNull(ticketQueryTs, "ticketQueryTs must not be null");
    }
}
