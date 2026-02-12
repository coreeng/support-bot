package com.coreeng.supportbot.metrics;

public record WeeklyActivityMetric(
        String type, // opened, closed, stale, escalated
        String week, // current, previous
        long count) {}
