package com.coreeng.supportbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "slack.creds")
public record SlackCredsProps(
        String token,
        String socketToken,
        String signingSecret
) {
}


