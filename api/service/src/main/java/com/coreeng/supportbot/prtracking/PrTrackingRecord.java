package com.coreeng.supportbot.prtracking;

import static java.util.Objects.requireNonNull;

import com.coreeng.supportbot.dbschema.enums.PrTrackingStatus;
import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record PrTrackingRecord(
        long id,
        long ticketId,
        String githubRepo,
        int prNumber,
        Instant prCreatedAt,
        @Nullable Instant slaDeadline,
        String owningTeam,
        boolean canAutoCloseTicket,
        PrTrackingStatus status,
        @Nullable Long escalationId,
        @Nullable Instant closedAt,
        @Nullable Duration slaRemaining,
        @Nullable Instant lastReviewAt,
        @Nullable Instant lastAuthorActivityAt) {
    public PrTrackingRecord {
        requireNonNull(githubRepo, "githubRepo must not be null");
        if (prNumber <= 0) {
            throw new IllegalArgumentException("prNumber must be positive, was " + prNumber);
        }
        requireNonNull(prCreatedAt, "prCreatedAt must not be null");
        requireNonNull(owningTeam, "owningTeam must not be null");
        if (owningTeam.isBlank()) {
            throw new IllegalArgumentException("owningTeam must not be blank");
        }
        requireNonNull(status, "status must not be null");
        if (status == PrTrackingStatus.CLOSED && closedAt == null) {
            throw new IllegalArgumentException("closedAt must not be null when status is CLOSED");
        }
        if (slaDeadline != null && slaRemaining != null) {
            throw new IllegalArgumentException("slaDeadline and slaRemaining must not both be set");
        }
        if (slaRemaining != null && slaRemaining.isNegative()) {
            throw new IllegalArgumentException("slaRemaining must not be negative");
        }
    }
}
