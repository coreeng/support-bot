package com.coreeng.supportbot.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ConditionalOnProperty(value = "metrics.enabled", havingValue = "true", matchIfMissing = false)
public class MetricsService {
    // TODO: Structure metric definitions better e.g. in a map
    private final MetricsRepository metricsRepository;
    private MultiGauge ticketGauge;
    private MultiGauge escalationGauge;
    private MultiGauge ratingGauge;
    private MultiGauge responseSlaGauge;
    private MultiGauge resolutionSlaGauge;
    // We create a separate metric for escalations tags as each escalation can have more than 1 tag associated
    private MultiGauge escalationsByTagGauge;
    private MultiGauge weeklyActivityGauge;
    private MultiGauge resolutionTimeByTagGauge;
    private final AtomicLong unattendedQueryCount = new AtomicLong(0);
    private final AtomicReference<Double> maxTicketAgeSecs = new AtomicReference<>(0.0);

    public MetricsService(MetricsRepository metricsRepository, MeterRegistry meterRegistry) {
        this.metricsRepository = metricsRepository;
        ticketGauge = MultiGauge.builder("supportbot_tickets").register(meterRegistry);
        escalationGauge = MultiGauge.builder("supportbot_escalations").register(meterRegistry);
        ratingGauge = MultiGauge.builder("supportbot_ratings").register(meterRegistry);
        responseSlaGauge = MultiGauge.builder("supportbot_response_sla_seconds").register(meterRegistry);
        resolutionSlaGauge =
                MultiGauge.builder("supportbot_resolution_sla_seconds").register(meterRegistry);
        escalationsByTagGauge =
                MultiGauge.builder("supportbot_escalations_by_tag").register(meterRegistry);
        weeklyActivityGauge = MultiGauge.builder("supportbot_weekly_activity").register(meterRegistry);
        resolutionTimeByTagGauge =
                MultiGauge.builder("supportbot_resolution_time_by_tag_seconds").register(meterRegistry);
        Gauge.builder("supportbot_unattended_queries", unattendedQueryCount, AtomicLong::doubleValue)
                .register(meterRegistry);
        Gauge.builder("supportbot_longest_active_ticket_seconds", maxTicketAgeSecs, AtomicReference::get)
                .register(meterRegistry);
    }

    @Scheduled(fixedRateString = "${metrics.refresh-interval:60s}")
    public void refreshMetrics() {
        log.debug("Refreshing metrics");
        try {
            List<MultiGauge.Row<Number>> rows = new ArrayList<>();

            for (TicketMetric ticket : metricsRepository.getTicketMetrics()) {
                rows.add(toRow(ticket));
            }

            List<MultiGauge.Row<Number>> escalationRows = new ArrayList<>();
            for (EscalationMetric escalation : metricsRepository.getEscalationMetrics()) {
                escalationRows.add(toEscalationRow(escalation));
            }
            escalationGauge.register(escalationRows, true);

            ticketGauge.register(rows, true);

            List<MultiGauge.Row<Number>> ratingRows = new ArrayList<>();
            for (RatingMetric rating : metricsRepository.getRatingMetrics()) {
                ratingRows.add(toRatingRow(rating));
            }
            ratingGauge.register(ratingRows, true);

            unattendedQueryCount.set(metricsRepository.getUnattendedQueryCount());

            ResponseSLAMetric responseSla = metricsRepository.getResponseSLAMetrics();
            if (responseSla != null) {
                responseSlaGauge.register(
                        List.of(
                                MultiGauge.Row.of(Tags.of("percentile", "p50"), responseSla.p50()),
                                MultiGauge.Row.of(Tags.of("percentile", "p90"), responseSla.p90())),
                        true);
            }

            ResolutionSLAMetric resolutionSla = metricsRepository.getResolutionSLAMetrics();
            if (resolutionSla != null) {
                resolutionSlaGauge.register(
                        List.of(
                                MultiGauge.Row.of(Tags.of("percentile", "p50"), resolutionSla.p50()),
                                MultiGauge.Row.of(Tags.of("percentile", "p75"), resolutionSla.p75()),
                                MultiGauge.Row.of(Tags.of("percentile", "p90"), resolutionSla.p90())),
                        true);
            }

            List<MultiGauge.Row<Number>> escalationsByTagRows = new ArrayList<>();
            for (EscalationByTagMetric metric : metricsRepository.getEscalationsByTag()) {
                escalationsByTagRows.add(MultiGauge.Row.of(Tags.of("tag", metric.tag()), metric.count()));
            }
            escalationsByTagGauge.register(escalationsByTagRows, true);

            List<MultiGauge.Row<Number>> weeklyActivityRows = new ArrayList<>();
            for (WeeklyActivityMetric metric : metricsRepository.getWeeklyActivity()) {
                weeklyActivityRows.add(
                        MultiGauge.Row.of(Tags.of("type", metric.type(), "week", metric.week()), metric.count()));
            }
            weeklyActivityGauge.register(weeklyActivityRows, true);

            List<MultiGauge.Row<Number>> resolutionTimeByTagRows = new ArrayList<>();
            for (ResolutionTimeByTagMetric metric : metricsRepository.getResolutionTimeByTag()) {
                resolutionTimeByTagRows.add(
                        MultiGauge.Row.of(Tags.of("tag", metric.tag(), "percentile", "p50"), metric.p50()));
                resolutionTimeByTagRows.add(
                        MultiGauge.Row.of(Tags.of("tag", metric.tag(), "percentile", "p90"), metric.p90()));
            }
            resolutionTimeByTagGauge.register(resolutionTimeByTagRows, true);

            maxTicketAgeSecs.set(metricsRepository.getLongestActiveTicketSeconds());
        } catch (Exception e) {
            log.error("Error refreshing metrics", e);
        }
    }

    private MultiGauge.Row<Number> toRow(TicketMetric ticket) {
        return MultiGauge.Row.of(
                Tags.of(
                        "status", ticket.status(),
                        "impact", ticket.impact(),
                        "team", ticket.team(),
                        "escalated", String.valueOf(ticket.escalated()),
                        "rated", String.valueOf(ticket.rated())),
                ticket.count());
    }

    private MultiGauge.Row<Number> toEscalationRow(EscalationMetric escalation) {
        return MultiGauge.Row.of(
                Tags.of(
                        "status", escalation.status(),
                        "team", escalation.team(),
                        "impact", escalation.impact()),
                escalation.count());
    }

    private MultiGauge.Row<Number> toRatingRow(RatingMetric rating) {
        return MultiGauge.Row.of(Tags.of("rating", String.valueOf(rating.rating())), rating.count());
    }
}
