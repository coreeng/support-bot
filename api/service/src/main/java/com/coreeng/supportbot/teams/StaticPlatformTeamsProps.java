package com.coreeng.supportbot.teams;

import com.coreeng.supportbot.teams.groups.GroupRef;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("platform-integration.teams-scraping.static")
public record StaticPlatformTeamsProps(boolean enabled, List<TeamConfig> teams) {
    public record TeamConfig(String name, @Nullable String code, GroupRef groupRef) {}
}
