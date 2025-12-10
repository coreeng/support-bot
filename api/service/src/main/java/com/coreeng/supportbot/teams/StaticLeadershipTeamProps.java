package com.coreeng.supportbot.teams;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("team.leadership.static")
public record StaticLeadershipTeamProps(
    boolean enabled,
    List<StaticSupportMember> members
) {
    public record StaticSupportMember(
        String email,
        String slackId
    ) {}
}