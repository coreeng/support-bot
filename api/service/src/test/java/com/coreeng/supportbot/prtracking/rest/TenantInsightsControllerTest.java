package com.coreeng.supportbot.prtracking.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.prtracking.PrTrackingRepository;
import com.coreeng.supportbot.prtracking.RepoInsights;
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
    void stats_returnsInsightsForValidWindow() {
        // given a repo with 10 PRs, 2 open, 1 escalated, 3 breached SLA
        List<RepoInsights> insights =
                List.of(new RepoInsights("org/repo-a", "team-foo", 10, 2, 1, 3, 3600.0, 7200.0, 86400.0));
        when(prTrackingRepository.getInsightsByRepo(7)).thenReturn(insights);

        // when requesting 7-day window
        ResponseEntity<List<RepoInsights>> response = controller.stats("7d");

        // then returns the repo insights with owning team
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).repo()).isEqualTo("org/repo-a");
        assertThat(response.getBody().get(0).owningTeam()).isEqualTo("team-foo");
        verify(prTrackingRepository).getInsightsByRepo(7);
    }

    @Test
    void stats_returnsMultipleReposWithDifferentTeams() {
        // given two repos owned by different teams, with escalations and breaches
        List<RepoInsights> insights = List.of(
                new RepoInsights("org/repo-a", "team-foo", 5, 1, 1, 2, 3600.0, 7200.0, 86400.0),
                new RepoInsights("org/repo-b", "team-bar", 3, 0, 0, 0, 1800.0, 3600.0, 43200.0));
        when(prTrackingRepository.getInsightsByRepo(30)).thenReturn(insights);

        // when requesting stats
        ResponseEntity<List<RepoInsights>> response = controller.stats("30d");

        // then returns both repos with their owning teams
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody().get(0).owningTeam()).isEqualTo("team-foo");
        assertThat(response.getBody().get(1).owningTeam()).isEqualTo("team-bar");
    }

    @Test
    void stats_accepts30DayWindow() {
        // given no PRs in the last 30 days
        when(prTrackingRepository.getInsightsByRepo(30)).thenReturn(List.of());

        // when requesting 30-day window
        ResponseEntity<List<RepoInsights>> response = controller.stats("30d");

        // then queries for 30 days
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(prTrackingRepository).getInsightsByRepo(30);
    }

    @Test
    void stats_accepts90DayWindow() {
        // given no PRs in the last 90 days
        when(prTrackingRepository.getInsightsByRepo(90)).thenReturn(List.of());

        // when requesting 90-day window
        ResponseEntity<List<RepoInsights>> response = controller.stats("90d");

        // then queries for 90 days
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(prTrackingRepository).getInsightsByRepo(90);
    }

    @Test
    void stats_returnsBadRequestForInvalidWindow() {
        // when requesting an unsupported window
        ResponseEntity<List<RepoInsights>> response = controller.stats("15d");

        // then rejects with 400, never hits the database
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(prTrackingRepository);
    }
}
