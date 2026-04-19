package com.coreeng.supportbot.prtracking.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.enums.EscalationTeam;
import com.coreeng.supportbot.prtracking.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class TenantInsightsControllerTest {

    @Mock
    private PrTrackingRepository prTrackingRepository;

    @Mock
    private com.coreeng.supportbot.enums.EscalationTeamsRegistry escalationTeamsRegistry;

    private TenantInsightsController controller;

    private static final LocalDate TO = LocalDate.of(2026, 3, 25);

    @BeforeEach
    void setUp() {
        controller = new TenantInsightsController(prTrackingRepository, escalationTeamsRegistry);
    }

    @Test
    void prStats_returnsInsightsForDateRange() {
        // given a repo with 10 PRs, 2 open, 1 escalated, 3 breached SLA
        LocalDate from = LocalDate.of(2026, 3, 1);
        List<RepoInsights> insights =
                List.of(new RepoInsights("org/repo-a", "team-foo", 10, 2, 1, 3, 3600.0, 7200.0, 86400.0, true));
        when(prTrackingRepository.getInsightsByRepo(from, TO)).thenReturn(insights);

        // when requesting with a date range
        List<RepoInsights> response = controller.prStats(from, TO);

        // then returns the repo insights with owning team
        assertThat(response).hasSize(1);
        assertThat(response.get(0).repo()).isEqualTo("org/repo-a");
        assertThat(response.get(0).owningTeam()).isEqualTo("team-foo");
        verify(prTrackingRepository).getInsightsByRepo(from, TO);
    }

    @Test
    void prStats_returnsMultipleReposWithDifferentTeams() {
        // given two repos owned by different teams, with escalations and breaches
        LocalDate from = LocalDate.of(2026, 2, 1);
        List<RepoInsights> insights = List.of(
                new RepoInsights("org/repo-a", "team-foo", 5, 1, 1, 2, 3600.0, 7200.0, 86400.0, true),
                new RepoInsights("org/repo-b", "team-bar", 3, 0, 0, 0, 1800.0, 3600.0, 43200.0, false));
        when(prTrackingRepository.getInsightsByRepo(from, TO)).thenReturn(insights);

        // when requesting stats
        List<RepoInsights> response = controller.prStats(from, TO);

        // then returns both repos with their owning teams and correct hasSla flags
        assertThat(response).hasSize(2);
        assertThat(response.get(0).owningTeam()).isEqualTo("team-foo");
        assertThat(response.get(0).hasSla()).isTrue();
        assertThat(response.get(1).owningTeam()).isEqualTo("team-bar");
        assertThat(response.get(1).hasSla()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {7, 30, 90})
    void prStats_acceptsVariousDateRanges(int days) {
        // given no PRs in the date range
        LocalDate from = TO.minusDays(days);
        when(prTrackingRepository.getInsightsByRepo(from, TO)).thenReturn(List.of());

        // when requesting the range
        List<RepoInsights> response = controller.prStats(from, TO);

        // then queries for the correct range
        assertThat(response).isEmpty();
        verify(prTrackingRepository).getInsightsByRepo(from, TO);
    }

    @Test
    void prStats_returnsAllTimeWhenNoDatesProvided() {
        // given no date range (null params = all-time query)
        when(prTrackingRepository.getInsightsByRepo(null, null)).thenReturn(List.of());

        // when requesting without dates
        List<RepoInsights> response = controller.prStats(null, null);

        // then queries with null dates (all time)
        assertThat(response).isEmpty();
        verify(prTrackingRepository).getInsightsByRepo(null, null);
    }

    @Test
    void prStats_acceptsDateFromOnly() {
        // given only dateFrom provided (open-ended: from date to now)
        LocalDate from = LocalDate.of(2026, 3, 1);
        when(prTrackingRepository.getInsightsByRepo(from, null)).thenReturn(List.of());

        // when requesting with dateFrom only
        List<RepoInsights> response = controller.prStats(from, null);

        // then queries with dateFrom and null dateTo
        assertThat(response).isEmpty();
        verify(prTrackingRepository).getInsightsByRepo(from, null);
    }

    @Test
    void prStats_acceptsDateToOnly() {
        // given only dateTo provided (open-ended: beginning of time to date)
        when(prTrackingRepository.getInsightsByRepo(null, TO)).thenReturn(List.of());

        // when requesting with dateTo only
        List<RepoInsights> response = controller.prStats(null, TO);

        // then queries with null dateFrom and dateTo
        assertThat(response).isEmpty();
        verify(prTrackingRepository).getInsightsByRepo(null, TO);
    }

    @Test
    void prStats_acceptsSameDayRange() {
        // given dateFrom equals dateTo (single-day query)
        when(prTrackingRepository.getInsightsByRepo(TO, TO)).thenReturn(List.of());

        // when requesting a single day
        List<RepoInsights> response = controller.prStats(TO, TO);

        // then accepts the request
        assertThat(response).isEmpty();
        verify(prTrackingRepository).getInsightsByRepo(TO, TO);
    }

    @Test
    void prStats_throwsForInvertedDateRange() {
        // when dateFrom is after dateTo
        LocalDate from = LocalDate.of(2026, 4, 1);
        LocalDate to = LocalDate.of(2026, 3, 1);

        // then rejects with 400 and descriptive message, never hits the database
        assertThatThrownBy(() -> controller.prStats(from, to))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("dateFrom must not be after dateTo");
        verifyNoInteractions(prTrackingRepository);
    }

    @Test
    void escalationBreakdown_returnsBreakdownForDateRange() {
        // given 10 PR tickets: 5 bot-escalated, 2 manually escalated
        LocalDate from = LocalDate.of(2026, 3, 1);
        when(prTrackingRepository.getEscalationBreakdown(from, TO)).thenReturn(new EscalationBreakdown(10, 5, 2));

        // when requesting breakdown
        EscalationBreakdown response = controller.escalationBreakdown(from, TO);

        // then returns the counts
        assertThat(response.totalPrTickets()).isEqualTo(10);
        assertThat(response.botEscalatedTickets()).isEqualTo(5);
        assertThat(response.manuallyEscalatedTickets()).isEqualTo(2);
        verify(prTrackingRepository).getEscalationBreakdown(from, TO);
    }

    @Test
    void escalationBreakdown_throwsForInvertedDateRange() {
        // when dateFrom is after dateTo
        LocalDate from = LocalDate.of(2026, 4, 1);
        LocalDate to = LocalDate.of(2026, 3, 1);

        // then rejects with 400, never hits the database
        assertThatThrownBy(() -> controller.escalationBreakdown(from, to))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("dateFrom must not be after dateTo");
        verifyNoInteractions(prTrackingRepository);
    }

    @Test
    void inFlightPrs_delegatesToRepository() {
        // given
        Instant now = Instant.parse("2026-03-25T12:00:00Z");
        InFlightPr pr = new InFlightPr(
                "org/repo-a",
                101,
                "https://github.com/org/repo-a/pull/101",
                "OPEN",
                "reviewer",
                now.minusSeconds(7200),
                now.plusSeconds(86400),
                null,
                null,
                "team-foo",
                "C_CHAN",
                "1700000000.000001",
                null,
                true);
        when(prTrackingRepository.findAllInFlight(null)).thenReturn(List.of(pr));

        // when
        List<InFlightPrResponse> result = controller.inFlightPrs(null);

        // then — sla-backed PR has hasSla=true
        assertThat(result).hasSize(1);
        assertThat(result.get(0).githubRepo()).isEqualTo("org/repo-a");
        assertThat(result.get(0).prNumber()).isEqualTo(101);
        assertThat(result.get(0).hasSla()).isTrue();
        verify(prTrackingRepository).findAllInFlight(null);
    }

    @Test
    void inFlightPrs_hasSlaFalseForNoSlaPr() {
        // given — a no-SLA PR: both slaDeadline and slaRemainingSeconds are null
        Instant now = Instant.parse("2026-03-25T12:00:00Z");
        InFlightPr noSlaPr = new InFlightPr(
                "org/no-sla-repo",
                200,
                "https://github.com/org/no-sla-repo/pull/200",
                "OPEN",
                "reviewer",
                now.minusSeconds(3600),
                null,
                null,
                null,
                "team-foo",
                "C_CHAN",
                "1700000000.000010",
                null,
                false);
        when(prTrackingRepository.findAllInFlight(null)).thenReturn(List.of(noSlaPr));

        // when
        List<InFlightPrResponse> result = controller.inFlightPrs(null);

        // then — no-SLA PR must report hasSla=false
        assertThat(result.get(0).hasSla()).isFalse();
    }

    @Test
    void inFlightPrs_hasSlaTrueForPausedSla() {
        // given — a PR with a paused SLA: slaDeadline is null, slaRemainingSeconds is set
        Instant now = Instant.parse("2026-03-25T12:00:00Z");
        InFlightPr pausedPr = new InFlightPr(
                "org/repo-a",
                201,
                "https://github.com/org/repo-a/pull/201",
                "CHANGES_REQUESTED",
                "reviewer",
                now.minusSeconds(7200),
                null,
                3600L,
                null,
                "team-foo",
                "C_CHAN",
                "1700000000.000011",
                null,
                true);
        when(prTrackingRepository.findAllInFlight(null)).thenReturn(List.of(pausedPr));

        // when
        List<InFlightPrResponse> result = controller.inFlightPrs(null);

        // then — paused SLA still means hasSla=true
        assertThat(result.get(0).hasSla()).isTrue();
    }

    @Test
    void inFlightPrs_resolvesTeamLabel() {
        // given — registry knows about team-foo
        Instant now = Instant.parse("2026-03-25T12:00:00Z");
        InFlightPr pr = new InFlightPr(
                "org/repo-a",
                102,
                "https://github.com/org/repo-a/pull/102",
                "OPEN",
                "reviewer",
                now.minusSeconds(3600),
                now.plusSeconds(86400),
                null,
                null,
                "team-foo",
                "C_CHAN",
                "1700000000.000002",
                null,
                true);
        when(prTrackingRepository.findAllInFlight(null)).thenReturn(List.of(pr));
        when(escalationTeamsRegistry.findEscalationTeamByCode("team-foo"))
                .thenReturn(new EscalationTeam("Foo Team", "team-foo", "SG001"));

        // when
        List<InFlightPrResponse> result = controller.inFlightPrs(null);

        // then — owningTeamLabel is the team's label from the registry
        assertThat(result.get(0).owningTeamLabel()).isEqualTo("Foo Team");
    }

    @Test
    void inFlightPrs_fallsBackToCodeWhenTeamNotFound() {
        // given — registry does not know about unknown-team
        Instant now = Instant.parse("2026-03-25T12:00:00Z");
        InFlightPr pr = new InFlightPr(
                "org/repo-b",
                103,
                "https://github.com/org/repo-b/pull/103",
                "OPEN",
                "reviewer",
                now.minusSeconds(1800),
                now.plusSeconds(43200),
                null,
                null,
                "unknown-team",
                "C_CHAN",
                "1700000000.000003",
                null,
                true);
        when(prTrackingRepository.findAllInFlight(null)).thenReturn(List.of(pr));
        when(escalationTeamsRegistry.findEscalationTeamByCode("unknown-team")).thenReturn(null);

        // when
        List<InFlightPrResponse> result = controller.inFlightPrs(null);

        // then — owningTeamLabel falls back to the raw team code
        assertThat(result.get(0).owningTeamLabel()).isEqualTo("unknown-team");
    }

    @Test
    void inFlightPrs_passesTeamFilterToRepository() {
        // given
        when(prTrackingRepository.findAllInFlight("team-bar")).thenReturn(List.of());

        // when
        List<InFlightPrResponse> result = controller.inFlightPrs("team-bar");

        // then
        assertThat(result).isEmpty();
        verify(prTrackingRepository).findAllInFlight("team-bar");
    }
}
