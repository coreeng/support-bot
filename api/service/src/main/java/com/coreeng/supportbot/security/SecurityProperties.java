package com.coreeng.supportbot.security;

import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security")
public record SecurityProperties(
        JwtProperties jwt,
        OAuth2Properties oauth2,
        CorsProperties cors,
        TestBypassProperties testBypass,
        AllowListProperties allowList) {
    public SecurityProperties {
        if (allowList == null) {
            allowList = new AllowListProperties(List.of(), List.of());
        }
    }

    public record JwtProperties(String secret, Duration expiration) {
        public JwtProperties {
            if (expiration == null) {
                expiration = Duration.ofHours(24);
            }
        }
    }

    public record OAuth2Properties(String redirectUri) {}

    public record CorsProperties(@Nullable String allowedOrigins) {}

    public record TestBypassProperties(boolean enabled) {}

    public record AllowListProperties(List<String> emails, List<String> domains) {
        public AllowListProperties {
            if (emails == null) emails = List.of();
            if (domains == null) domains = List.of();
        }
    }
}
