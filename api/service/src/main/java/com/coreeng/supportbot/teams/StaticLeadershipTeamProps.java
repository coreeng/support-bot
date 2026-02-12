package com.coreeng.supportbot.teams;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("team.leadership.static")
public record StaticLeadershipTeamProps(boolean enabled, List<StaticSupportMember> members) {
    public record StaticSupportMember(String email, String slackId) {}
}
