package com.coreeng.supportbot.teams;

import com.coreeng.supportbot.teams.groups.GroupRef;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("platform-integration.teams-scraping.static")
public record StaticPlatformTeamsProps(boolean enabled, List<TeamConfig> teams) {
    /** @param attributes extra values about the team, keyed by attribute name, e.g. the one team groups are built from */
    public record TeamConfig(
            String name,
            @Nullable String code,
            GroupRef groupRef,
            @Nullable Map<String, String> attributes) {}
}
