package com.coreeng.supportbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("support-team")
public record SupportTeamProps(
    String name,
    String slackGroupId
) {
}
