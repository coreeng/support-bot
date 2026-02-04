package com.coreeng.supportbot.dashboard.rest;

public record WeeklyComparison(String metric, long thisWeek, long lastWeek, long change) {}
