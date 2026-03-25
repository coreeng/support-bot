package com.coreeng.supportbot.prtracking.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.prtracking.PrTrackingRepository;
import com.coreeng.supportbot.prtracking.RepoInsights;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class TenantInsightsControllerTest {

    @Mock
    private PrTrackingRepository prTrackingRepository;

    private TenantInsightsController controller;

    private static final LocalDate TO = LocalDate.of(2026, 3, 25);

    @BeforeEach
    void setUp() {
        controller = new TenantInsightsController(prTrackingRepository);
    }

    @Test
    void enabled_returnsTrue() {
        // when
        TenantInsightsController.FeatureStatus status = controller.enabled();

        // then
        assertThat(status.enabled()).isTrue();
    }

    @Test
    void prStats_returnsInsightsForDateRange() {
        // given a repo with 10 PRs, 2 open, 1 escalated, 3 breached SLA
        LocalDate from = LocalDate.of(2026, 3, 1);
        List<RepoInsights> insights =
                List.of(new RepoInsights("org/repo-a", "team-foo", 10, 2, 1, 3, 3600.0, 7200.0, 86400.0));
        when(prTrackingRepository.getInsightsByRepo(from, TO)).thenReturn(insights);

        // when requesting with a date range
        ResponseEntity<List<RepoInsights>> response = controller.prStats(from, TO);

        // then returns the repo insights with owning team
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).repo()).isEqualTo("org/repo-a");
        assertThat(response.getBody().get(0).owningTeam()).isEqualTo("team-foo");
        verify(prTrackingRepository).getInsightsByRepo(from, TO);
    }

    @Test
    void prStats_returnsMultipleReposWithDifferentTeams() {
        // given two repos owned by different teams, with escalations and breaches
        LocalDate from = LocalDate.of(2026, 2, 1);
        List<RepoInsights> insights = List.of(
                new RepoInsights("org/repo-a", "team-foo", 5, 1, 1, 2, 3600.0, 7200.0, 86400.0),
                new RepoInsights("org/repo-b", "team-bar", 3, 0, 0, 0, 1800.0, 3600.0, 43200.0));
        when(prTrackingRepository.getInsightsByRepo(from, TO)).thenReturn(insights);

        // when requesting stats
        ResponseEntity<List<RepoInsights>> response = controller.prStats(from, TO);

        // then returns both repos with their owning teams
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody().get(0).owningTeam()).isEqualTo("team-foo");
        assertThat(response.getBody().get(1).owningTeam()).isEqualTo("team-bar");
    }

    @Test
    void prStats_accepts7DayRange() {
        // given no PRs in the last 7 days
        LocalDate from = TO.minusDays(7);
        when(prTrackingRepository.getInsightsByRepo(from, TO)).thenReturn(List.of());

        // when requesting 7-day range
        ResponseEntity<List<RepoInsights>> response = controller.prStats(from, TO);

        // then queries for 7 days
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(prTrackingRepository).getInsightsByRepo(from, TO);
    }

    @Test
    void prStats_accepts30DayRange() {
        // given no PRs in the last 30 days
        LocalDate from = TO.minusDays(30);
        when(prTrackingRepository.getInsightsByRepo(from, TO)).thenReturn(List.of());

        // when requesting 30-day range
        ResponseEntity<List<RepoInsights>> response = controller.prStats(from, TO);

        // then queries for 30 days
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(prTrackingRepository).getInsightsByRepo(from, TO);
    }

    @Test
    void prStats_accepts90DayRange() {
        // given no PRs in the last 90 days
        LocalDate from = TO.minusDays(90);
        when(prTrackingRepository.getInsightsByRepo(from, TO)).thenReturn(List.of());

        // when requesting 90-day range
        ResponseEntity<List<RepoInsights>> response = controller.prStats(from, TO);

        // then queries for 90 days
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(prTrackingRepository).getInsightsByRepo(from, TO);
    }

    @Test
    void prStats_returnsAllTimeWhenNoDatesProvided() {
        // given no date range (null params = all-time query)
        when(prTrackingRepository.getInsightsByRepo(null, null)).thenReturn(List.of());

        // when requesting without dates
        ResponseEntity<List<RepoInsights>> response = controller.prStats(null, null);

        // then queries with null dates (all time)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(prTrackingRepository).getInsightsByRepo(null, null);
    }

    @Test
    void prStats_acceptsDateFromOnly() {
        // given only dateFrom provided (open-ended: from date to now)
        LocalDate from = LocalDate.of(2026, 3, 1);
        when(prTrackingRepository.getInsightsByRepo(from, null)).thenReturn(List.of());

        // when requesting with dateFrom only
        ResponseEntity<List<RepoInsights>> response = controller.prStats(from, null);

        // then queries with dateFrom and null dateTo
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(prTrackingRepository).getInsightsByRepo(from, null);
    }

    @Test
    void prStats_acceptsDateToOnly() {
        // given only dateTo provided (open-ended: beginning of time to date)
        when(prTrackingRepository.getInsightsByRepo(null, TO)).thenReturn(List.of());

        // when requesting with dateTo only
        ResponseEntity<List<RepoInsights>> response = controller.prStats(null, TO);

        // then queries with null dateFrom and dateTo
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(prTrackingRepository).getInsightsByRepo(null, TO);
    }

    @Test
    void prStats_acceptsSameDayRange() {
        // given dateFrom equals dateTo (single-day query)
        when(prTrackingRepository.getInsightsByRepo(TO, TO)).thenReturn(List.of());

        // when requesting a single day
        ResponseEntity<List<RepoInsights>> response = controller.prStats(TO, TO);

        // then accepts the request
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(prTrackingRepository).getInsightsByRepo(TO, TO);
    }

    @Test
    void prStats_returnsBadRequestForInvertedDateRange() {
        // when dateFrom is after dateTo
        LocalDate from = LocalDate.of(2026, 4, 1);
        LocalDate to = LocalDate.of(2026, 3, 1);
        ResponseEntity<List<RepoInsights>> response = controller.prStats(from, to);

        // then rejects with 400, never hits the database
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(prTrackingRepository);
    }
}
