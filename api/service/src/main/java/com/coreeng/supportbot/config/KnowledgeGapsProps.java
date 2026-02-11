package com.coreeng.supportbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "knowledge-gaps")
public record KnowledgeGapsProps(
    boolean enabled
) {
}

