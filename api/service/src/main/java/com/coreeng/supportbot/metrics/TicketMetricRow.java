package com.coreeng.supportbot.metrics;

public record TicketMetricRow(
        String status,
        String impact,
        String team,
        boolean escalated,
        boolean rated,
        long count
) {}
