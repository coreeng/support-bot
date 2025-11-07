package com.coreeng.supportbot.teams;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("platform-integration.fetch")
public record PlatformTeamsFetchProps(
    int maxConcurrency,
    Duration timeout
) {
}

