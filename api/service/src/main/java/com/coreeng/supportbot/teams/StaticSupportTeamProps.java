package com.coreeng.supportbot.teams;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("team.support.static")
public record StaticSupportTeamProps(boolean enabled, List<StaticSupportMember> members) {
    public record StaticSupportMember(String email, String slackId) {}
}
