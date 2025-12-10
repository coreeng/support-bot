package com.coreeng.supportbot.teams;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("team.leadership")
public record SupportLeadershipTeamProps(
        String name,
        String code,
        String slackGroupId
) {}