package com.coreeng.supportbot.prtracking;

import static java.util.Objects.requireNonNull;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Active-PR projection used by the /in-flight-prs endpoint.
 *
 * <p>This record is a <b>read projection</b> — it is populated inside the jOOQ fetch lambda in
 * {@link JdbcPrTrackingRepository#findAllInFlight}. It therefore enforces no cross-field
 * invariants between {@code hasSla}, {@code slaDeadline}, and {@code slaRemainingSeconds}, matching
 * the policy on {@link InFlightPrResponse} and {@link RepoInsights}. Throwing here would 500 the
 * whole in-flight tab over one malformed row (pre-V15 backfill gap, mid-update read, or a bug we
 * want to observe rather than hide). The frontend degrades quirky triples into an "SLA data
 * missing" badge with console diagnostics.
 *
 * <p>{@code hasSla} is read from the persisted {@code has_sla} column (written once on insert,
 * never mutated) rather than derived from the SLA fields. Matches {@link RepoInsights#hasSla()}
 * — one concept, one read path — and avoids silent misclassification if ever widened to CLOSED
 * rows (which clear both SLA fields).
 *
 * <p>Write-path invariants (e.g. {@code slaDeadline}/{@code slaRemainingSeconds} mutual
 * exclusivity across pauseSla/resumeSla transitions) are enforced by single-statement atomic
 * UPDATEs in {@link JdbcPrTrackingRepository} and by the source-level guards in
 * {@code JdbcPrTrackingRepositoryInvariantTest}, not by this read constructor.
 */
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
        @Nullable Instant escalatedAt,
        boolean hasSla) {
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
