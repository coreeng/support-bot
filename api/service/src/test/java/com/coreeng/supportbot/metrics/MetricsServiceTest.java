package com.coreeng.supportbot.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsServiceTest {

    @Mock
    private MetricsRepository metricsRepository;

    private MeterRegistry meterRegistry;
    private MetricsService metricsService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsService = new MetricsService(metricsRepository, meterRegistry);
    }

    @Test
    void registersMetricWithCorrectTags() {
        when(metricsRepository.getTicketMetrics())
            .thenReturn(List.of(
                    new TicketMetric("opened", "productionBlocking", "infra-integration", true, false, 5)
            ));

        metricsService.refreshMetrics();

        Gauge metric = meterRegistry.find("supportbot_tickets")
            .tag("status", "opened")
            .tag("impact", "productionBlocking")
            .tag("team", "infra-integration")
            .tag("escalated", "true")
            .tag("rated", "false")
            .gauge();

        assertThat(metric).isNotNull();
        assertThat(metric.value()).isEqualTo(5.0);
    }

    @Test
    void registersMetricEscalations() {
        when(metricsRepository.getEscalationMetrics())
                .thenReturn(List.of(
                        new EscalationMetric("pending", "infra-integration", "productionBlocking", 3)
                ));

        metricsService.refreshMetrics();

        Gauge metric = meterRegistry.find("supportbot_escalations")
                .tag("status", "pending")
                .tag("impact", "productionBlocking")
                .tag("team", "infra-integration")
                .gauge();

        assertThat(metric).isNotNull();
        assertThat(metric.value()).isEqualTo(3.0);
    }

    @Test
    void registersRatingMetrics() {
        when(metricsRepository.getRatingMetrics()).thenReturn(List.of(
                new RatingMetric(4, 10),
                new RatingMetric(5, 5)
        ));

        metricsService.refreshMetrics();

        Gauge rating1 = meterRegistry.find("supportbot_ratings")
                .tag("rating", "4")
                .gauge();
        assertThat(rating1).isNotNull();
        assertThat(rating1.value()).isEqualTo(10.0);

        Gauge rating2 = meterRegistry.find("supportbot_ratings")
                .tag("rating", "5")
                .gauge();
        assertThat(rating2).isNotNull();
        assertThat(rating2.value()).isEqualTo(5);
    }

    @Test
    void registersUnattendedQueryMetric() {
        when(metricsRepository.getUnattendedQueryCount()).thenReturn(15L);

        metricsService.refreshMetrics();

        Gauge metric = meterRegistry.find("supportbot_unattended_queries").gauge();
        assertThat(metric).isNotNull();
        assertThat(metric.value()).isEqualTo(15.0);
    }

    @Test
    void registersResponseSLAMetrics() {
        when(metricsRepository.getResponseSLAMetrics())
                .thenReturn(new ResponseSLAMetric(3600.0, 86_400.0));

        metricsService.refreshMetrics();

        Gauge p50 = meterRegistry.find("supportbot_response_sla_seconds")
                .tag("percentile", "p50")
                .gauge();
        assertThat(p50).isNotNull();
        assertThat(p50.value()).isEqualTo(3600.0);

        Gauge p90 = meterRegistry.find("supportbot_response_sla_seconds")
                .tag("percentile", "p90")
                .gauge();
        assertThat(p90).isNotNull();
        assertThat(p90.value()).isEqualTo(86_400.0);
    }

    @Test
    void registersResolutionSLAMetrics() {
        when(metricsRepository.getResolutionSLAMetrics())
                .thenReturn(new ResolutionSLAMetric(7200.0, 86_400.0, 172_800.0));

        metricsService.refreshMetrics();

        Gauge p50 = meterRegistry.find("supportbot_resolution_sla_seconds")
                .tag("percentile", "p50")
                .gauge();
        assertThat(p50).isNotNull();
        assertThat(p50.value()).isEqualTo(7200.0);

        Gauge p75 = meterRegistry.find("supportbot_resolution_sla_seconds")
                .tag("percentile", "p75")
                .gauge();
        assertThat(p75).isNotNull();
        assertThat(p75.value()).isEqualTo(86_400.0);

        Gauge p90 = meterRegistry.find("supportbot_resolution_sla_seconds")
                .tag("percentile", "p90")
                .gauge();
        assertThat(p90).isNotNull();
        assertThat(p90.value()).isEqualTo(172_800.0);
    }

    @Test
    void registersEscalationsByTagMetrics() {
        when(metricsRepository.getEscalationsByTag()).thenReturn(List.of(
                new EscalationByTagMetric("networking", 10),
                new EscalationByTagMetric("vault", 5)
        ));

        metricsService.refreshMetrics();

        Gauge networking = meterRegistry.find("supportbot_escalations_by_tag")
                .tag("tag", "networking")
                .gauge();
        assertThat(networking).isNotNull();
        assertThat(networking.value()).isEqualTo(10.0);

        Gauge vault = meterRegistry.find("supportbot_escalations_by_tag")
                .tag("tag", "vault")
                .gauge();
        assertThat(vault).isNotNull();
        assertThat(vault.value()).isEqualTo(5.0);
    }

    @Test
    void registersLongestActiveTicketMetric() {
        when(metricsRepository.getLongestActiveTicketSeconds()).thenReturn(604_800.0); // 7 days in seconds

        metricsService.refreshMetrics();

        Gauge metric = meterRegistry.find("supportbot_longest_active_ticket_seconds").gauge();
        assertThat(metric).isNotNull();
        assertThat(metric.value()).isEqualTo(604_800.0);
    }

    @Test
    void registersWeeklyActivityMetrics() {
        when(metricsRepository.getWeeklyActivity()).thenReturn(List.of(
                new WeeklyActivityMetric("opened", "current", 10),
                new WeeklyActivityMetric("opened", "previous", 15),
                new WeeklyActivityMetric("closed", "current", 8),
                new WeeklyActivityMetric("closed", "previous", 12)
        ));

        metricsService.refreshMetrics();

        Gauge openedCurrent = meterRegistry.find("supportbot_weekly_activity")
                .tag("type", "opened")
                .tag("week", "current")
                .gauge();
        assertThat(openedCurrent).isNotNull();
        assertThat(openedCurrent.value()).isEqualTo(10.0);

        Gauge openedPrevious = meterRegistry.find("supportbot_weekly_activity")
                .tag("type", "opened")
                .tag("week", "previous")
                .gauge();
        assertThat(openedPrevious).isNotNull();
        assertThat(openedPrevious.value()).isEqualTo(15.0);

        Gauge closedCurrent = meterRegistry.find("supportbot_weekly_activity")
                .tag("type", "closed")
                .tag("week", "current")
                .gauge();
        assertThat(closedCurrent).isNotNull();
        assertThat(closedCurrent.value()).isEqualTo(8.0);
    }

    @Test
    void registersResolutionTimeByTagMetrics() {
        when(metricsRepository.getResolutionTimeByTag()).thenReturn(List.of(
                new ResolutionTimeByTagMetric("networking", 3600.0, 7200.0),
                new ResolutionTimeByTagMetric("vault", 1800.0, 5400.0)
        ));

        metricsService.refreshMetrics();

        Gauge networkingP50 = meterRegistry.find("supportbot_resolution_time_by_tag_seconds")
                .tag("tag", "networking")
                .tag("percentile", "p50")
                .gauge();
        assertThat(networkingP50).isNotNull();
        assertThat(networkingP50.value()).isEqualTo(3600.0);

        Gauge networkingP90 = meterRegistry.find("supportbot_resolution_time_by_tag_seconds")
                .tag("tag", "networking")
                .tag("percentile", "p90")
                .gauge();
        assertThat(networkingP90).isNotNull();
        assertThat(networkingP90.value()).isEqualTo(7200.0);

        Gauge vaultP50 = meterRegistry.find("supportbot_resolution_time_by_tag_seconds")
                .tag("tag", "vault")
                .tag("percentile", "p50")
                .gauge();
        assertThat(vaultP50).isNotNull();
        assertThat(vaultP50.value()).isEqualTo(1800.0);
    }
}
