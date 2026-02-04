package com.coreeng.supportbot.dashboard.rest;

import com.coreeng.supportbot.dashboard.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class DashboardControllerTest {

    private DashboardService service;
    private DashboardController controller;

    @BeforeEach
    void setUp() {
        service = mock(DashboardService.class);
        controller = new DashboardController(service);
    }

    @Test
    void shouldReturnResponsePercentiles() {
        // given
        var expected = new ResponsePercentiles(60, 120);
        when(service.getResponsePercentiles("2026-01-01", "2026-01-31"))
                .thenReturn(expected);

        // when
        var result = controller.getResponsePercentiles("2026-01-01", "2026-01-31");

        // then
        assertThat(result.p50()).isEqualTo(60);
        assertThat(result.p90()).isEqualTo(120);
    }

    @Test
    void shouldReturnResponsePercentilesWithNoDates() {
        // given
        var expected = new ResponsePercentiles(999.0, 2000.0);
        when(service.getResponsePercentiles(null, null))
                .thenReturn(expected);

        // when
        var result = controller.getResponsePercentiles(null, null);

        // then
        assertThat(result.p50()).isEqualTo(999.0);
        assertThat(result.p90()).isEqualTo(2000.0);
        verify(service).getResponsePercentiles(null, null);
        verifyNoMoreInteractions(service);
    }

    @Test
    void shouldReturnResponseDistribution() {
        // given
        var expected = List.of(60, 120, 180, 240);
        when(service.getResponseDistribution("2025-01-01", "2025-01-31"))
                .thenReturn(expected);

        // when
        var result = controller.getResponseDistribution("2025-01-01", "2025-01-31");

        // then
        assertThat(result).containsExactly(60, 120, 180, 240);
        verify(service).getResponseDistribution("2025-01-01", "2025-01-31");
        verifyNoMoreInteractions(service);
    }

    @Test
    void shouldReturnResponseUnattendedCount() {
        // given
        var expected = new ResponseUnattendedCount(10);
        when(service.getResponseUnattendedCount("2025-01-01", "2025-01-31"))
                .thenReturn(expected);

        // when
        var result = controller.getResponseUnattendedCount("2025-01-01", "2025-01-31");

        // then
        assertThat(result.count()).isEqualTo(10);
        verify(service).getResponseUnattendedCount("2025-01-01", "2025-01-31");
        verifyNoMoreInteractions(service);
    }

    @Test
    void shouldReturnResolutionPercentiles() {
        // given
        var expected = new ResolutionPercentiles(3600.0, 7200.0, 14_400.0);
        when(service.getResolutionPercentiles("2024-01-01", "2024-01-31"))
                .thenReturn(expected);

        // when
        var result = controller.getResolutionPercentiles("2024-01-01", "2024-01-31");

        // then
        assertThat(result.p50()).isEqualTo(3600.0);
        assertThat(result.p75()).isEqualTo(7200.0);
        assertThat(result.p90()).isEqualTo(14_400.0);
        verify(service).getResolutionPercentiles("2024-01-01", "2024-01-31");
        verifyNoMoreInteractions(service);
    }

    @Test
    void shouldReturnResolutionDurationDistribution() {
        // given
        var expected = List.of(
                new ResolutionDurationBucket("< 15 min", 10, 0, 900),
                new ResolutionDurationBucket("1-2 hours", 5, 3600, 7200)
        );
        when(service.getResolutionDurationDistribution("2024-01-01", "2024-01-31"))
                .thenReturn(expected);

        // when
        var result = controller.getResolutionDurationDistribution("2024-01-01", "2024-01-31");

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).label()).isEqualTo("< 15 min");
        assertThat(result.get(0).count()).isEqualTo(10);
        assertThat(result.get(1).label()).isEqualTo("1-2 hours");
        assertThat(result.get(1).count()).isEqualTo(5);
        verify(service).getResolutionDurationDistribution("2024-01-01", "2024-01-31");
        verifyNoMoreInteractions(service);
    }

    @Test
    void shouldReturnResolutionTimesByWeek() {
        // given
        var expected = List.of(
                new ResolutionWeeklyPercentiles("2023-01-06", 3600.0, 7200.0, 14_400.0),
                new ResolutionWeeklyPercentiles("2023-01-13", 5400.0, 9000.0, 18_000.0)
        );
        when(service.getResolutionTimesByWeek("2023-01-01", "2023-01-31"))
                .thenReturn(expected);

        // when
        var result = controller.getResolutionTimesByWeek("2023-01-01", "2023-01-31");

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).week()).isEqualTo("2023-01-06");
        assertThat(result.get(0).p50()).isEqualTo(3600.0);
        assertThat(result.get(1).week()).isEqualTo("2023-01-13");
        assertThat(result.get(1).p90()).isEqualTo(18_000.0);
        verify(service).getResolutionTimesByWeek("2023-01-01", "2023-01-31");
        verifyNoMoreInteractions(service);
    }

    @Test
    void shouldReturnResolutionOpenTicketAges() {
        // given
        var expected = new ResolutionOpenTicketAges(86_400.0, 259_200.0);
        when(service.getResolutionOpenTicketAges("2023-01-01", "2023-01-31"))
                .thenReturn(expected);

        // when
        var result = controller.getResolutionOpenTicketAges("2023-01-01", "2023-01-31");

        // then
        assertThat(result.p50()).isEqualTo(86_400.0);
        assertThat(result.p90()).isEqualTo(259_200.0);
        verify(service).getResolutionOpenTicketAges("2023-01-01", "2023-01-31");
        verifyNoMoreInteractions(service);
    }

    @Test
    void shouldReturnResolutionTimeByTag() {
        // given
        var expected = List.of(
                new ResolutionTimeByTag("networking", 3600.0, 7200.0),
                new ResolutionTimeByTag("github actions", 1800.0, 5400.0)
        );
        when(service.getResolutionTimeByTag("2023-01-01", "2023-01-31"))
                .thenReturn(expected);

        // when
        var result = controller.getResolutionTimeByTag("2023-01-01", "2023-01-31");

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).tag()).isEqualTo("networking");
        assertThat(result.get(0).p50()).isEqualTo(3600.0);
        assertThat(result.get(1).tag()).isEqualTo("github actions");
        assertThat(result.get(1).p90()).isEqualTo(5400.0);
        verify(service).getResolutionTimeByTag("2023-01-01", "2023-01-31");
        verifyNoMoreInteractions(service);
    }

    @Test
    void shouldReturnEscalationAvgDurationByTag() {
        // given
        var expected = List.of(
                new EscalationAvgDurationByTag("networking", 7200.0),
                new EscalationAvgDurationByTag("github actions", 3600.0)
        );
        when(service.getEscalationAvgDurationByTag("2022-01-01", "2022-01-31"))
                .thenReturn(expected);

        // when
        var result = controller.getEscalationAvgDurationByTag("2022-01-01", "2022-01-31");

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).tag()).isEqualTo("networking");
        assertThat(result.get(0).avgDurationSeconds()).isEqualTo(7200.0);
        verify(service).getEscalationAvgDurationByTag("2022-01-01", "2022-01-31");
        verifyNoMoreInteractions(service);
    }

    @Test
    void shouldReturnEscalationCountByTag() {
        // given
        var expected = List.of(
                new EscalationCountByTag("networking", 15),
                new EscalationCountByTag("github actions", 8)
        );
        when(service.getEscalationCountByTag("2022-01-01", "2022-01-31"))
                .thenReturn(expected);

        // when
        var result = controller.getEscalationCountByTag("2022-01-01", "2022-01-31");

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).tag()).isEqualTo("networking");
        assertThat(result.get(0).count()).isEqualTo(15);
        verify(service).getEscalationCountByTag("2022-01-01", "2022-01-31");
        verifyNoMoreInteractions(service);
    }

    @Test
    void shouldReturnEscalationCountTrend() {
        // given
        var expected = List.of(
                new EscalationCountTrend("2022-01-15", 3),
                new EscalationCountTrend("2022-01-16", 5)
        );
        when(service.getEscalationCountTrend("2022-01-01", "2022-01-31"))
                .thenReturn(expected);

        // when
        var result = controller.getEscalationCountTrend("2022-01-01", "2022-01-31");

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).date()).isEqualTo("2022-01-15");
        assertThat(result.get(0).escalations()).isEqualTo(3);
        verify(service).getEscalationCountTrend("2022-01-01", "2022-01-31");
        verifyNoMoreInteractions(service);
    }

    @Test
    void shouldReturnEscalationByTeam() {
        // given
        var expected = List.of(
                new EscalationByTeam("Platform", 12),
                new EscalationByTeam("DevOps", 4)
        );
        when(service.getEscalationByTeam("2022-01-01", "2022-01-31"))
                .thenReturn(expected);

        // when
        var result = controller.getEscalationByTeam("2022-01-01", "2022-01-31");

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).teamName()).isEqualTo("Platform");
        assertThat(result.get(0).totalEscalations()).isEqualTo(12);
        verify(service).getEscalationByTeam("2022-01-01", "2022-01-31");
        verifyNoMoreInteractions(service);
    }

    @Test
    void shouldReturnEscalationByImpact() {
        // given
        var expected = List.of(
                new EscalationByImpact("Production Blocking", 10),
                new EscalationByImpact("Abnormal Behaviour", 3)
        );
        when(service.getEscalationByImpact("2022-01-01", "2022-01-31"))
                .thenReturn(expected);

        // when
        var result = controller.getEscalationByImpact("2022-01-01", "2022-01-31");

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).impactLevel()).isEqualTo("Production Blocking");
        assertThat(result.get(0).totalEscalations()).isEqualTo(10);
        verify(service).getEscalationByImpact("2022-01-01", "2022-01-31");
        verifyNoMoreInteractions(service);
    }

    @Test
    void shouldReturnWeeklyTicketCounts() {
        // given
        var expected = List.of(
                new WeeklyTicketCounts("2021-01-04", 10, 5, 2, 1),
                new WeeklyTicketCounts("2021-01-11", 8, 7, 3, 0)
        );
        when(service.getWeeklyTicketCounts("2021-01-01", "2021-01-31"))
                .thenReturn(expected);

        // when
        var result = controller.getWeeklyTicketCounts("2021-01-01", "2021-01-31");

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).week()).isEqualTo("2021-01-04");
        assertThat(result.get(0).opened()).isEqualTo(10);
        assertThat(result.get(0).closed()).isEqualTo(5);
        assertThat(result.get(0).escalated()).isEqualTo(2);
        assertThat(result.get(0).stale()).isEqualTo(1);
        assertThat(result.get(1).week()).isEqualTo("2021-01-11");
        verify(service).getWeeklyTicketCounts("2021-01-01", "2021-01-31");
        verifyNoMoreInteractions(service);
    }

    @Test
    void shouldReturnWeeklyComparison() {
        // given
        var expected = List.of(
                new WeeklyComparison("opened", 12, 8, 4),
                new WeeklyComparison("closed", 10, 10, 0),
                new WeeklyComparison("stale", 2, 5, -3),
                new WeeklyComparison("escalated", 3, 1, 2)
        );
        when(service.getWeeklyComparison()).thenReturn(expected);

        // when
        var result = controller.getWeeklyComparison();

        // then
        assertThat(result).hasSize(4);
        assertThat(result.get(0).metric()).isEqualTo("opened");
        assertThat(result.get(0).thisWeek()).isEqualTo(12);
        assertThat(result.get(0).lastWeek()).isEqualTo(8);
        assertThat(result.get(0).change()).isEqualTo(4);
        assertThat(result.get(2).change()).isEqualTo(-3);
        verify(service).getWeeklyComparison();
        verifyNoMoreInteractions(service);
    }

    @Test
    void shouldReturnWeeklyTopEscalatedTags() {
        // given
        var expected = List.of(
                new WeeklyTopEscalatedTag("networking", 7),
                new WeeklyTopEscalatedTag("github actions", 4)
        );
        when(service.getWeeklyTopEscalatedTags()).thenReturn(expected);

        // when
        var result = controller.getWeeklyTopEscalatedTags();

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).tag()).isEqualTo("networking");
        assertThat(result.get(0).count()).isEqualTo(7);
        assertThat(result.get(1).tag()).isEqualTo("github actions");
        assertThat(result.get(1).count()).isEqualTo(4);
        verify(service).getWeeklyTopEscalatedTags();
        verifyNoMoreInteractions(service);
    }

}
