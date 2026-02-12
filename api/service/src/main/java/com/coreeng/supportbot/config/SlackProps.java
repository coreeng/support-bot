package com.coreeng.supportbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "slack")
public record SlackProps(boolean enableRequestVerification, CredsProps creds, ClientProps client) {
    record ClientProps(String methodsBaseUrl) {}

    record CredsProps(String token, String socketToken, String signingSecret) {}
}
