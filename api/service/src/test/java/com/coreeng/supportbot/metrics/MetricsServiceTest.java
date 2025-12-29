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
        metricsService.init();
    }

    @Test
    void registersMetricWithCorrectTags() {
        when(metricsRepository.getTicketMetrics())
            .thenReturn(List.of(
                    new TicketMetricRow("opened", "productionBlocking", "infra-integration", true, false, 5)
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
}
