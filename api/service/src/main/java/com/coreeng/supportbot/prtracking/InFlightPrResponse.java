package com.coreeng.supportbot.prtracking;

import com.coreeng.supportbot.prtracking.source.Provider;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * API payload for a single in-flight PR row on the tenant-requests dashboard.
 *
 * <p>The internal field is named {@code repo} but is serialised as {@code githubRepo} to preserve
 * the v1 wire format — the frontend's in-flight tab reads {@code pr.githubRepo} unchanged.
 * Removing the {@code @JsonProperty} would silently blank the repo column in the UI.
 *
 * <p>Deliberately enforces no cross-field invariants on {@code hasSla} × {@code slaDeadline} ×
 * {@code slaRemainingSeconds}. The "healthy" triples are:
 *
 * <ul>
 *   <li>{@code (hasSla=true,  slaDeadline=X,    slaRemainingSeconds=null)} — active SLA
 *   <li>{@code (hasSla=true,  slaDeadline=null, slaRemainingSeconds=X)}    — paused SLA
 *   <li>{@code (hasSla=false, slaDeadline=null, slaRemainingSeconds=null)} — no-SLA repo
 * </ul>
 *
 * Any other combination shouldn't occur under the normal insert + state-transition code paths,
 * but could arise from the pre-V15 backfill gap or a rare mid-update read (see
 * {@code V15__pr_tracking_has_sla.sql}). The frontend's {@code slaInfo()} degrades those cases
 * into an "SLA data missing" badge with console diagnostics; throwing here would 500 the whole
 * /in-flight-prs endpoint, blanking the tab for every user over one bad row.
 */
public record InFlightPrResponse(
        @JsonIgnore Provider provider,
        @JsonProperty("githubRepo") String repo,
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
        @Nullable Instant escalatedAt,
        boolean hasSla) {

    public InFlightPrResponse(InFlightPr pr, String owningTeamLabel) {
        this(
                pr.provider(),
                pr.repo(),
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
                pr.escalatedAt(),
                pr.hasSla());
    }
}
