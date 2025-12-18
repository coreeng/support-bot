package com.coreeng.supportbot.metrics;

import com.coreeng.supportbot.ticket.DetailedTicket;
import com.coreeng.supportbot.ticket.TicketQueryService;
import com.coreeng.supportbot.ticket.TicketsQuery;
import com.coreeng.supportbot.util.Page;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
@RequiredArgsConstructor
public class MetricsService {
    private static final String metricName = "supportbot_tickets";

    private final TicketQueryService queryService;
    private final MeterRegistry meterRegistry;
    private final Map<String, AtomicLong> ticketMetrics = new ConcurrentHashMap<>();

    @Scheduled(fixedRateString = "${metrics.refreshIntervalMs:60000}")
    public void refreshMetrics() {
        log.debug("Refreshing metrics");
        try {
            Page<DetailedTicket> tickets = queryService.findDetailedTicketByQuery(
                TicketsQuery.builder().unlimited(true).build()
            );

            for (DetailedTicket dt : tickets.content()) {
                registerTicketMetric(dt);
            }
        } catch (Exception e) {
            log.error("Error refreshing metrics", e);
        }
    }

    // DetailedTicket includes escalation data needed for the escalated tag hence why we use
    private void registerTicketMetric(DetailedTicket dt) {
        String ticketId = String.valueOf(dt.ticket().id().id());
        String status = dt.ticket().status().name();
        String impact = Optional.ofNullable(dt.ticket().impact()).orElse("unknown");
        String team = Optional.ofNullable(dt.ticket().team()).orElse("unassigned");
        String escalated = String.valueOf(dt.escalated());
        String rated = String.valueOf(dt.ticket().ratingSubmitted());

        ticketMetrics.computeIfAbsent(ticketId, k -> {
            AtomicLong value = new AtomicLong(1);
            Gauge.builder(metricName, value, AtomicLong::doubleValue)
                .tag("ticketId", ticketId)
                .tag("status", status)
                .tag("impact", impact)
                .tag("team", team)
                .tag("escalated", escalated)
                .tag("rated", rated)
                .register(meterRegistry);
            return value;
        });
    }
}
