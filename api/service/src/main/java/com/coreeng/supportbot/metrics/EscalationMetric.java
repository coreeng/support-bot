package com.coreeng.supportbot.metrics;

public record EscalationMetric(
        String status,
        String team,
        String impact,
        long count
) {}
