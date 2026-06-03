package com.coreeng.supportbot.prtracking;

import static java.util.Objects.requireNonNull;

import com.coreeng.supportbot.prtracking.source.Provider;
import com.fasterxml.jackson.annotation.JsonIgnore;

public record RepoInsights(
        @JsonIgnore Provider provider,
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
        requireNonNull(provider, "provider must not be null");
        requireNonNull(repo, "repo must not be null");
        requireNonNull(owningTeam, "owningTeam must not be null");
        if (prCount < 0 || openCount < 0 || escalatedCount < 0 || breachedCount < 0) {
            throw new IllegalArgumentException("counts must not be negative");
        }
        if (p50Seconds < 0 || p90Seconds < 0 || p99Seconds < 0) {
            throw new IllegalArgumentException("percentile seconds must not be negative");
        }
        // Note: we intentionally do not throw when (!hasSla && breachedCount > 0). On the
        // /pr-stats path, TenantInsightsController replaces hasSla with the present-day config
        // value, which is decoupled from the historical breachedCount — so the combination
        // legitimately appears on the wire for a repo reconfigured SLA → no-SLA. Other callers
        // of this record (today there are none beyond JdbcPrTrackingRepository + TenantInsightsController;
        // tests may construct it directly) should treat hasSla and breachedCount as independent
        // signals, not an enforced pair.
    }

    /** Returns a copy with {@code hasSla} replaced. Prefer this over positional reconstruction. */
    public RepoInsights withHasSla(boolean newHasSla) {
        if (newHasSla == hasSla) {
            return this;
        }
        return new RepoInsights(
                provider,
                repo,
                owningTeam,
                prCount,
                openCount,
                escalatedCount,
                breachedCount,
                p50Seconds,
                p90Seconds,
                p99Seconds,
                newHasSla);
    }
}
