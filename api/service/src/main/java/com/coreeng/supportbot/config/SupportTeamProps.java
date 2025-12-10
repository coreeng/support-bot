package com.coreeng.supportbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("team.support")
public record SupportTeamProps(
    String name,
    String code,
    String slackGroupId
) {
}
