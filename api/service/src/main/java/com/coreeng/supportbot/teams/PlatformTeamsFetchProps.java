package com.coreeng.supportbot.teams;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("platform-integration.fetch")
public record PlatformTeamsFetchProps(int maxConcurrency, Duration timeout, boolean ignoreUnknownTeams) {}
