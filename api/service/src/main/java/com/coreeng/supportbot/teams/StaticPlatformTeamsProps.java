package com.coreeng.supportbot.teams;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("platform-integration.teams-scraping.static")
public record StaticPlatformTeamsProps(boolean enabled, List<TeamConfig> teams) {
    public record TeamConfig(String name, String groupRef) {}
}
