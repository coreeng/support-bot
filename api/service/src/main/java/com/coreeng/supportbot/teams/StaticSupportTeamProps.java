package com.coreeng.supportbot.teams;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("support-team.static")
public record StaticSupportTeamProps(
    boolean enabled,
    List<StaticSupportMember> members
) {
    public record StaticSupportMember(
        String email,
        String slackId
    ) {}
}