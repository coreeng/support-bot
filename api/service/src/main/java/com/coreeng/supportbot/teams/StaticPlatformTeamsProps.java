package com.coreeng.supportbot.teams;

import com.coreeng.supportbot.teams.groups.GroupRef;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("platform-integration.teams-scraping.static")
public record StaticPlatformTeamsProps(boolean enabled, List<TeamConfig> teams) {
    /**
     * {@code code} is the immutable identity (defaults to {@code name} when omitted). {@code name}
     * is the display value — set an explicit {@code code} to change the display without orphaning
     * references (PT-518).
     */
    public record TeamConfig(String name, @Nullable String code, GroupRef groupRef) {}
}
