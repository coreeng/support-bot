package com.coreeng.supportbot.prtracking;

import com.coreeng.supportbot.config.PrTrackingProps;
import com.coreeng.supportbot.enums.EscalationTeam;
import com.coreeng.supportbot.enums.EscalationTeamsRegistry;
import com.coreeng.supportbot.prtracking.source.Provider;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Query and presentation orchestration for tenant pull-request insights. */
@Service
@ConditionalOnProperty(name = "pr-review-tracking.enabled", havingValue = "true")
@RequiredArgsConstructor
public class TenantInsightsService {
    private static final String INVALID_DATE_RANGE_MESSAGE = "dateFrom must not be after dateTo";

    private final PrTrackingRepository prTrackingRepository;
    private final EscalationTeamsRegistry escalationTeamsRegistry;
    private final PrTrackingProps prTrackingProps;

    public List<RepoInsights> prStats(@Nullable LocalDate dateFrom, @Nullable LocalDate dateTo) {
        validateDateRange(dateFrom, dateTo);
        return replaceHasSlaWithCurrentConfig(prTrackingRepository.getInsightsByRepo(dateFrom, dateTo));
    }

    public RequestBreakdown requestBreakdown(@Nullable LocalDate dateFrom, @Nullable LocalDate dateTo) {
        validateDateRange(dateFrom, dateTo);
        return prTrackingRepository.getRequestBreakdown(dateFrom, dateTo);
    }

    public List<InFlightPrResponse> inFlightPrs(@Nullable String team) {
        return prTrackingRepository.findAllInFlight(team).stream()
                .map(pr -> new InFlightPrResponse(pr, resolveTeamLabel(pr.owningTeam())))
                .toList();
    }

    private static void validateDateRange(@Nullable LocalDate dateFrom, @Nullable LocalDate dateTo) {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_DATE_RANGE_MESSAGE);
        }
    }

    // Replace the persisted has_sla aggregate with current configuration while leaving historical
    // metrics untouched, so the repository badge reflects present-day policy.
    private List<RepoInsights> replaceHasSlaWithCurrentConfig(List<RepoInsights> insights) {
        Set<ProviderRepoKey> configuredSlaRepos = prTrackingProps.repositories().stream()
                .filter(repository -> !repository.hasNoSla())
                .map(repository -> new ProviderRepoKey(
                        repository.provider(), repository.name().toLowerCase(Locale.ROOT)))
                .collect(Collectors.toUnmodifiableSet());
        return insights.stream()
                .map(insight -> insight.withHasSla(configuredSlaRepos.contains(
                        new ProviderRepoKey(insight.provider(), insight.repo().toLowerCase(Locale.ROOT)))))
                .toList();
    }

    private String resolveTeamLabel(String teamCode) {
        EscalationTeam team = escalationTeamsRegistry.findEscalationTeamByCode(teamCode);
        return team != null ? team.label() : teamCode;
    }

    private record ProviderRepoKey(Provider provider, String repo) {}
}
