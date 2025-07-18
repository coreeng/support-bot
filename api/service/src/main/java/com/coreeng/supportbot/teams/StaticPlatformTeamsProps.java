package com.coreeng.supportbot.teams;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("platform-integration.teams-scraping.static")
public record StaticPlatformTeamsProps(
    boolean enabled,
    List<TeamConfig> teams
) {
    public record TeamConfig(
        String name,
        String groupRef
    ) {}
}