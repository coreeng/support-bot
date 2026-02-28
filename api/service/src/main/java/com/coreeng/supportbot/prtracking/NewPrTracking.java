package com.coreeng.supportbot.prtracking;

import java.time.Instant;

public record NewPrTracking(
        long ticketId, String githubRepo, int prNumber, Instant prCreatedAt, Instant slaDeadline, String owningTeam) {}
