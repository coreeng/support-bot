package com.coreeng.supportbot.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "security")
public record SecurityProperties(
    JwtProperties jwt,
    OAuth2Properties oauth2,
    CorsProperties cors,
    TestBypassProperties testBypass
) {
    public record JwtProperties(
        String secret,
        Duration expiration
    ) {
        public JwtProperties {
            if (expiration == null) {
                expiration = Duration.ofHours(24);
            }
        }
    }

    public record OAuth2Properties(
        String redirectUri
    ) {}

    public record CorsProperties(
        String allowedOrigins
    ) {}

    public record TestBypassProperties(
        boolean enabled
    ) {}
}
