package com.coreeng.supportbot.dashboard.rest;

public record ResolutionDurationBucket(String label, long count, double minSeconds, double maxSeconds) {}
