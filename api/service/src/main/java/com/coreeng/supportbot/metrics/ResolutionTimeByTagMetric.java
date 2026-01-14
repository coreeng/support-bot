package com.coreeng.supportbot.metrics;

public record ResolutionTimeByTagMetric(
    String tag,
    Double p50,
    Double p90
) {}
