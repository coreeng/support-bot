package com.coreeng.supportbot.troubleshooting.router;

public record RouterUsage(String model, long inputTokens, long outputTokens, long durationMs) {}
