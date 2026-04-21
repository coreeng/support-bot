package com.coreeng.supportbot.prtracking.rest;

import com.coreeng.supportbot.config.PrTrackingProps;
import com.coreeng.supportbot.enums.EscalationTeam;
import com.coreeng.supportbot.enums.EscalationTeamsRegistry;
import com.coreeng.supportbot.prtracking.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/tenant-insights")
@ConditionalOnProperty(name = "pr-review-tracking.enabled", havingValue = "true")
@RequiredArgsConstructor
public class TenantInsightsController {

    private final PrTrackingRepository prTrackingRepository;
    private final EscalationTeamsRegistry escalationTeamsRegistry;
    private final PrTrackingProps prTrackingProps;

    @GetMapping("/pr-stats")
    public List<RepoInsights> prStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate dateTo) {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dateFrom must not be after dateTo");
        }
        return replaceHasSlaWithCurrentConfig(prTrackingRepository.getInsightsByRepo(dateFrom, dateTo));
    }

    /**
     * Replaces the persisted {@code has_sla} signal with the current SLA config, making the badge
     * on the repos health tab reflect present-state only: "is this repo SLA-tracked right now?".
     * Historical metrics (counts, percentiles, breached_count) still come from the DB aggregate.
     *
     * <p>This deliberately ignores {@code BOOL_OR(has_sla)}. Under this model:
     * <ul>
     *   <li>A repo currently in config with SLA → {@code hasSla=true}, even if every stored row
     *       has {@code has_sla=false} (e.g. pre-V15 closures that lost their signal on close).
     *   <li>A repo reconfigured from SLA → no-SLA → {@code hasSla=false}, even if it has
     *       historical SLA'd rows in the DB.
     *   <li>A repo removed from config → {@code hasSla=false} (no longer authoritative).
     * </ul>
     *
     * <p>Per-PR historical truth (was this PR created under SLA?) still lives in the persisted
     * {@code has_sla} column and is exposed via the in-flight-prs endpoint, which operates at row
     * granularity rather than repo granularity.
     */
    private List<RepoInsights> replaceHasSlaWithCurrentConfig(List<RepoInsights> insights) {
        // Config-side names are normalised to lowercase by PrTrackingProps#normalizeRepositoryName,
        // but DB github_repo values are whatever a historical caller inserted. Compare in lowercase
        // on both sides so a mismatched-case legacy row doesn't silently evade the current-state rule.
        Set<String> configuredSlaRepos = prTrackingProps.repositories().stream()
                .filter(r -> !r.hasNoSla())
                .map(r -> r.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        return insights.stream()
                .map(i -> i.withHasSla(configuredSlaRepos.contains(i.repo().toLowerCase(Locale.ROOT))))
                .toList();
    }

    @GetMapping("/escalation-breakdown")
    public EscalationBreakdown escalationBreakdown(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate dateTo) {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dateFrom must not be after dateTo");
        }
        return prTrackingRepository.getEscalationBreakdown(dateFrom, dateTo);
    }

    @GetMapping("/in-flight-prs")
    public List<InFlightPrResponse> inFlightPrs(@RequestParam(required = false) @Nullable String team) {
        return prTrackingRepository.findAllInFlight(team).stream()
                .map(pr -> new InFlightPrResponse(pr, resolveTeamLabel(pr.owningTeam())))
                .toList();
    }

    private String resolveTeamLabel(String teamCode) {
        EscalationTeam team = escalationTeamsRegistry.findEscalationTeamByCode(teamCode);
        return team != null ? team.label() : teamCode;
    }
}
