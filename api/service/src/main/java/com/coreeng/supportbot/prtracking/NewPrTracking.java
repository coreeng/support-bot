package com.coreeng.supportbot.prtracking;

import static java.util.Objects.requireNonNull;

import com.coreeng.supportbot.prtracking.source.Provider;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record NewPrTracking(
        long ticketId,
        Provider provider,
        String repo,
        int prNumber,
        Instant prCreatedAt,
        @Nullable Instant slaDeadline,
        String owningTeam,
        boolean canAutoCloseTicket) {
    public NewPrTracking {
        requireNonNull(provider, "provider must not be null");
        requireNonNull(repo, "repo must not be null");
        requireNonNull(prCreatedAt, "prCreatedAt must not be null");
        requireNonNull(owningTeam, "owningTeam must not be null");
    }

    public NewPrTracking(
            long ticketId,
            Provider provider,
            String repo,
            int prNumber,
            Instant prCreatedAt,
            @Nullable Instant slaDeadline,
            String owningTeam) {
        this(ticketId, provider, repo, prNumber, prCreatedAt, slaDeadline, owningTeam, true);
    }

    /**
     * Whether the PR was created under an SLA — i.e. the repo has an SLA configured for it.
     * <p>Centralised here (rather than inlined at the insert call site) so that if this record
     * ever grows fields relevant to SLA presence (e.g. an explicit paused-at-creation state), the
     * rule for the {@code has_sla} column updates in one place and stays in lock-step with the
     * V15 migration's back-fill predicate.
     */
    public boolean hasSla() {
        return slaDeadline != null;
    }
}
