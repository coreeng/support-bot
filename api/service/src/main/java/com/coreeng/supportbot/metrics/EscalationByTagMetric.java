package com.coreeng.supportbot.metrics;

public record EscalationByTagMetric(
    String tag,
    long count
) {}
