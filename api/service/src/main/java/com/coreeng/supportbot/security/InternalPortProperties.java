package com.coreeng.supportbot.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "internal-port")
public record InternalPortProperties(boolean enabled, int port) {
    public InternalPortProperties {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("internal-port.port must be between 0 and 65535");
        }
    }
}
