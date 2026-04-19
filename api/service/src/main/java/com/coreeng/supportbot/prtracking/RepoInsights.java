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
        double p99Seconds,
        boolean hasSla) {
    public RepoInsights {
        requireNonNull(repo, "repo must not be null");
        requireNonNull(owningTeam, "owningTeam must not be null");
        if (prCount < 0 || openCount < 0 || escalatedCount < 0 || breachedCount < 0) {
            throw new IllegalArgumentException("counts must not be negative");
        }
        if (p50Seconds < 0 || p90Seconds < 0 || p99Seconds < 0) {
            throw new IllegalArgumentException("percentile seconds must not be negative");
        }
        // Note: we intentionally do not throw when (!hasSla && breachedCount > 0). That combination
        // shouldn't occur in practice, but it could arise under the pre-V15 backfill gap (PRs closed before V15 lost the
        // SLA signal; see V15__pr_tracking_has_sla.sql)
    }
}
