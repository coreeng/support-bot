package com.coreeng.supportbot.metrics;

public record ResolutionSLAMetric(
    Double p50,
    Double p75,
    Double p90
) {}
