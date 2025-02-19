package com.coreeng.supportbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "slack.escalation")
public record SlackEscalationProps(
    String channelId
) {
}
