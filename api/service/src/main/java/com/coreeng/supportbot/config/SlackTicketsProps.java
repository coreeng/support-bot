package com.coreeng.supportbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "slack.ticket")
public record SlackTicketsProps(
        String channelId,
        String expectedInitialReaction,
        String responseInitialReaction,
        String resolvedReaction,
        String escalatedReaction) {}
