package com.coreeng.supportbot.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.security.SecurityProperties;
import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class CorsConfigTest {

    private static SecurityProperties createProperties(@Nullable String allowedOrigins) {
        return new SecurityProperties(
                new SecurityProperties.JwtProperties("test-secret-minimum-256-bits-for-testing", Duration.ofHours(1)),
                new SecurityProperties.OAuth2Properties("http://localhost:3000/auth/callback"),
                new SecurityProperties.CorsProperties(allowedOrigins),
                new SecurityProperties.TestBypassProperties(false),
                new SecurityProperties.AllowListProperties(List.of(), List.of()));
    }

    @Test
    void shouldReturnNoCorsConfigWhenDomainsIsEmpty() {
        var corsConfig = new CorsConfig();
        CorsConfigurationSource source = corsConfig.corsConfigurationSource(createProperties(""));

        CorsConfiguration config = source.getCorsConfiguration(new MockHttpServletRequest());

        // Empty source returns null - no CORS headers will be added
        assertThat(config).isNull();
    }

    @Test
    void shouldReturnNoCorsConfigWhenDomainsIsNull() {
        var corsConfig = new CorsConfig();
        CorsConfigurationSource source = corsConfig.corsConfigurationSource(createProperties(null));

        CorsConfiguration config = source.getCorsConfiguration(new MockHttpServletRequest());

        // Empty source returns null - no CORS headers will be added
        assertThat(config).isNull();
    }

    @Test
    void shouldAllowAllOriginsWhenWildcardIsExplicitlySet() {
        var corsConfig = new CorsConfig();
        CorsConfigurationSource source = corsConfig.corsConfigurationSource(createProperties("*"));

        CorsConfiguration config = source.getCorsConfiguration(new MockHttpServletRequest());

        assertThat(config).isNotNull();
        assertThat(config.getAllowedOriginPatterns()).containsExactly("*");
    }

    @Test
    void shouldAllowAllOriginsWhenWildcardWithWhitespace() {
        var corsConfig = new CorsConfig();
        CorsConfigurationSource source = corsConfig.corsConfigurationSource(createProperties("  *  "));

        CorsConfiguration config = source.getCorsConfiguration(new MockHttpServletRequest());

        assertThat(config).isNotNull();
        assertThat(config.getAllowedOriginPatterns()).containsExactly("*");
    }

    @Test
    void shouldRestrictToPatternWhenSingleDomainIsSet() {
        var corsConfig = new CorsConfig();
        CorsConfigurationSource source = corsConfig.corsConfigurationSource(createProperties("example.com"));

        CorsConfiguration config = source.getCorsConfiguration(new MockHttpServletRequest());

        assertThat(config).isNotNull();
        assertThat(config.getAllowedOriginPatterns())
                .containsExactlyInAnyOrder("https://*.example.com", "https://example.com");
    }

    @Test
    void shouldSupportMultipleCommaSeparatedDomains() {
        var corsConfig = new CorsConfig();
        CorsConfigurationSource source =
                corsConfig.corsConfigurationSource(createProperties("example.com,other.org,third.io"));

        CorsConfiguration config = source.getCorsConfiguration(new MockHttpServletRequest());

        assertThat(config).isNotNull();
        assertThat(config.getAllowedOriginPatterns())
                .containsExactlyInAnyOrder(
                        "https://*.example.com",
                        "https://example.com",
                        "https://*.other.org",
                        "https://other.org",
                        "https://*.third.io",
                        "https://third.io");
    }

    @Test
    void shouldHandleWhitespaceInCommaSeparatedDomains() {
        var corsConfig = new CorsConfig();
        CorsConfigurationSource source =
                corsConfig.corsConfigurationSource(createProperties("example.com, other.org , third.io"));

        CorsConfiguration config = source.getCorsConfiguration(new MockHttpServletRequest());

        assertThat(config).isNotNull();
        assertThat(config.getAllowedOriginPatterns())
                .containsExactlyInAnyOrder(
                        "https://*.example.com",
                        "https://example.com",
                        "https://*.other.org",
                        "https://other.org",
                        "https://*.third.io",
                        "https://third.io");
    }

    @Test
    @SuppressWarnings("NullAway") // Testing configuration values
    void shouldAllowStandardHttpMethods() {
        var corsConfig = new CorsConfig();
        CorsConfigurationSource source = corsConfig.corsConfigurationSource(createProperties("*"));

        CorsConfiguration config = source.getCorsConfiguration(new MockHttpServletRequest());

        assertThat(config.getAllowedMethods())
                .containsExactlyInAnyOrder("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS");
    }

    @Test
    @SuppressWarnings("NullAway") // Testing configuration values
    void shouldAllowCredentials() {
        var corsConfig = new CorsConfig();
        CorsConfigurationSource source = corsConfig.corsConfigurationSource(createProperties("*"));

        CorsConfiguration config = source.getCorsConfiguration(new MockHttpServletRequest());

        assertThat(config.getAllowCredentials()).isTrue();
    }
}
