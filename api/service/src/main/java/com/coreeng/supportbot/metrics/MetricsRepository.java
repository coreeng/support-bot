package com.coreeng.supportbot.metrics;

import java.util.List;

public interface MetricsRepository {
    List<TicketMetric> getTicketMetrics();

    List<EscalationMetric> getEscalationMetrics();

    List<RatingMetric> getRatingMetrics();

    long getUnattendedQueryCount();

    ResponseSLAMetric getResponseSLAMetrics();

    ResolutionSLAMetric getResolutionSLAMetrics();

    List<EscalationByTagMetric> getEscalationsByTag();

    Double getLongestActiveTicketSeconds();

    List<WeeklyActivityMetric> getWeeklyActivity();

    List<ResolutionTimeByTagMetric> getResolutionTimeByTag();
}
