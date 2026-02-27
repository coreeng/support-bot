package com.coreeng.supportbot.config;

import java.time.Duration;

public record PrTrackingRepositoryProps(
        String name,
        String owningTeam,
        Duration sla) {}
