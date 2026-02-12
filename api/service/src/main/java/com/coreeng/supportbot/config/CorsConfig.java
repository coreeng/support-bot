package com.coreeng.supportbot.config;

import com.coreeng.supportbot.security.SecurityProperties;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS configuration supporting multiple domains via comma-separated list.
 *
 * <p>Configuration options for security.cors.allowed-origins:
 * <ul>
 *   <li>"*" - Allow all origins (development mode)</li>
 *   <li>"example.com,other.org" - Allow specific domains and their subdomains (HTTPS only)</li>
 *   <li>Empty/unset - No CORS configured (same-origin only, restrictive default)</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class CorsConfig {

    @Bean
    @Primary
    public CorsConfigurationSource corsConfigurationSource(SecurityProperties securityProperties) {
        String allowedOrigins =
                securityProperties.cors() != null ? securityProperties.cors().allowedOrigins() : null;

        // If not configured, return empty source (no CORS headers = same-origin only)
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            return new UrlBasedCorsConfigurationSource();
        }

        CorsConfiguration configuration = new CorsConfiguration();

        if ("*".equals(allowedOrigins.trim())) {
            // Explicit wildcard: allow all origins
            configuration.setAllowedOriginPatterns(List.of("*"));
        } else {
            // Build patterns from comma-separated domain list
            List<String> patterns = new ArrayList<>();

            // Parse comma-separated domains and create patterns for each
            Arrays.stream(allowedOrigins.split(","))
                    .map(String::trim)
                    .filter(domain -> !domain.isEmpty() && !"*".equals(domain))
                    .forEach(domain -> {
                        patterns.add("https://*." + domain); // Subdomains
                        patterns.add("https://" + domain); // Root domain
                    });

            if (patterns.isEmpty()) {
                // No valid domains after parsing, return empty source
                return new UrlBasedCorsConfigurationSource();
            }

            configuration.setAllowedOriginPatterns(patterns);
        }

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
