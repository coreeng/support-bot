package com.coreeng.supportbot.prtracking;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record InFlightPrResponse(
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
        String owningTeamLabel,
        String ticketChannelId,
        String ticketQueryTs,
        @Nullable Instant escalatedAt) {

    public InFlightPrResponse(InFlightPr pr, String owningTeamLabel) {
        this(
                pr.githubRepo(),
                pr.prNumber(),
                pr.prUrl(),
                pr.status(),
                pr.waitingOn(),
                pr.prCreatedAt(),
                pr.slaDeadline(),
                pr.slaRemainingSeconds(),
                pr.lastReviewAt(),
                pr.owningTeam(),
                owningTeamLabel,
                pr.ticketChannelId(),
                pr.ticketQueryTs(),
                pr.escalatedAt());
    }
}
