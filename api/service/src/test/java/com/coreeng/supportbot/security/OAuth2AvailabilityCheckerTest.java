package com.coreeng.supportbot.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class OAuth2AvailabilityCheckerTest {

    private static SecurityProperties createSecurityProperties(boolean testBypassEnabled) {
        return new SecurityProperties(
                new SecurityProperties.JwtProperties("test-secret", Duration.ofHours(24)),
                new SecurityProperties.OAuth2Properties("http://localhost:3000/auth/callback"),
                new SecurityProperties.CorsProperties(null),
                new SecurityProperties.TestBypassProperties(testBypassEnabled),
                new SecurityProperties.AllowListProperties(List.of(), List.of()));
    }

    @Test
    void dexAvailable_whenAllThreeCredentialsPresent() {
        var checker = new OAuth2AvailabilityChecker(
                createSecurityProperties(false), "dex-client-id", "dex-client-secret", "https://dex.example.com");

        assertTrue(checker.isOAuth2Available());
        assertEquals(List.of("dex"), checker.getAvailableProviders());
    }

    @Test
    void dexNotAvailable_whenMissingIssuerUri() {
        var checker = new OAuth2AvailabilityChecker(
                createSecurityProperties(false), "dex-client-id", "dex-client-secret", "");

        assertFalse(checker.isOAuth2Available());
        assertEquals(List.of(), checker.getAvailableProviders());
    }

    @Test
    void dexNotAvailable_whenMissingClientId() {
        var checker =
                new OAuth2AvailabilityChecker(createSecurityProperties(false), "", "dex-client-secret", "https://dex.example.com");

        assertFalse(checker.isOAuth2Available());
        assertEquals(List.of(), checker.getAvailableProviders());
    }

    @Test
    void dexNotAvailable_whenMissingClientSecret() {
        var checker =
                new OAuth2AvailabilityChecker(createSecurityProperties(false), "dex-client-id", "", "https://dex.example.com");

        assertFalse(checker.isOAuth2Available());
        assertEquals(List.of(), checker.getAvailableProviders());
    }

    @Test
    void noProvidersAvailable_whenNoCredentialsConfigured() {
        var checker = new OAuth2AvailabilityChecker(createSecurityProperties(false), "", "", "");

        assertFalse(checker.isOAuth2Available());
        assertEquals(List.of(), checker.getAvailableProviders());
    }

    @Test
    void whitespaceStrings_treatedAsBlank() {
        var checker =
                new OAuth2AvailabilityChecker(createSecurityProperties(false), "   ", "  \t  ", " \n ");

        assertFalse(checker.isOAuth2Available());
        assertEquals(List.of(), checker.getAvailableProviders());
    }
}
