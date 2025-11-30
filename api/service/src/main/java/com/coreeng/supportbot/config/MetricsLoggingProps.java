package com.coreeng.supportbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "logging.metrics")
public record MetricsLoggingProps(
    boolean enabled,
    String prefix
) {
}
