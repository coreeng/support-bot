package com.coreeng.supportbot.security;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    /**
     * @param loginProviders When non-empty, only these OAuth2 registration ids ({@code google}, {@code azure},
     *                       {@code dex}) are registered and advertised. When empty, every fully configured provider
     *                       is used.
     */
    public record OAuth2Properties(String redirectUri, List<String> loginProviders) {
        public OAuth2Properties {
            if (loginProviders == null) {
                loginProviders = List.of();
            } else {
                var normalized = new ArrayList<String>();
                for (String p : loginProviders) {
                    if (p != null && !p.isBlank()) {
                        normalized.add(p.trim().toLowerCase(Locale.ROOT));
                    }
                }
                loginProviders = List.copyOf(normalized);
            }
        }

        /** Convenience for tests: no allowlist (all configured providers). */
        public static OAuth2Properties withRedirectOnly(String redirectUri) {
            return new OAuth2Properties(redirectUri, List.of());
        }
    }

    public record CorsProperties(@Nullable String allowedOrigins) {}

    public record TestBypassProperties(boolean enabled) {}

    public record AllowListProperties(List<String> emails, List<String> domains) {
        public AllowListProperties {
            if (emails == null) emails = List.of();
            if (domains == null) domains = List.of();
        }
    }
}
