package com.coreeng.supportbot.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
@ConditionalOnProperty(value = "metrics.enabled", havingValue = "true", matchIfMissing = false)
public class MetricsService {
    private static final String metricName = "supportbot_tickets";

    private final MetricsRepository metricsRepository;
    private final MeterRegistry meterRegistry;
    private MultiGauge ticketGauge;

    public MetricsService(MetricsRepository metricsRepository, MeterRegistry meterRegistry) {
        this.metricsRepository = metricsRepository;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    void init() {
        ticketGauge = MultiGauge.builder(metricName).register(meterRegistry);
    }

    @Scheduled(fixedRateString = "${metrics.refresh-interval:60s}")
    public void refreshMetrics() {
        log.debug("Refreshing metrics");
        try {
            List<MultiGauge.Row<Number>> rows = new ArrayList<>();

            for (TicketMetricRow ticket : metricsRepository.getTicketMetrics()) {
                rows.add(toRow(ticket));
            }

            ticketGauge.register(rows, true);
        } catch (Exception e) {
            log.error("Error refreshing metrics", e);
        }
    }

    private MultiGauge.Row<Number> toRow(TicketMetricRow ticket) {
        return MultiGauge.Row.of(
            Tags.of(
                "status", ticket.status(),
                "impact", ticket.impact(),
                "team", ticket.team(),
                "escalated", String.valueOf(ticket.escalated()),
                "rated", String.valueOf(ticket.rated())
            ),
            ticket.count()
        );
    }
}
