package com.coreeng.supportbot.prtracking;

import static java.util.Objects.requireNonNull;

public record RepoInsights(
        String repo,
        String owningTeam,
        long prCount,
        long openCount,
        long escalatedCount,
        long breachedCount,
        double p50Seconds,
        double p90Seconds,
        double p99Seconds) {
    public RepoInsights {
        requireNonNull(repo, "repo must not be null");
        requireNonNull(owningTeam, "owningTeam must not be null");
    }
}
