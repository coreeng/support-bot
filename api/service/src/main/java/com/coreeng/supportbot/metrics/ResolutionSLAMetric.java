package com.coreeng.supportbot.metrics;

public record ResolutionSLAMetric(
    double p50,
    double p75,
    double p90
) {}
