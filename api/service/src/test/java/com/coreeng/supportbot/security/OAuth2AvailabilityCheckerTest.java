package com.coreeng.supportbot.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class OAuth2AvailabilityCheckerTest {

    private static SecurityProperties createSecurityProperties(boolean testBypassEnabled) {
        return createSecurityProperties(testBypassEnabled, List.of());
    }

    private static SecurityProperties createSecurityProperties(
            boolean testBypassEnabled, List<String> loginProviders) {
        return new SecurityProperties(
                new SecurityProperties.JwtProperties("test-secret", Duration.ofHours(24)),
                new SecurityProperties.OAuth2Properties("http://localhost:3000/auth/callback", loginProviders),
                new SecurityProperties.CorsProperties(null),
                new SecurityProperties.TestBypassProperties(testBypassEnabled),
                new SecurityProperties.AllowListProperties(List.of(), List.of()));
    }

    @Test
    void googleAvailable_whenBothCredentialsPresent() {
        // given
        var checker = new OAuth2AvailabilityChecker(
                createSecurityProperties(false), "google-client-id", "google-client-secret", "", "", "", "", "", "");

        // then
        assertTrue(checker.isOAuth2Available());
        assertEquals(List.of("google"), checker.getAvailableProviders());
    }

    @Test
    void googleNotAvailable_whenOnlyClientIdPresent() {
        // given
        var checker = new OAuth2AvailabilityChecker(
                createSecurityProperties(false), "google-client-id", "", "", "", "", "", "", "");

        // then
        assertFalse(checker.isOAuth2Available());
        assertEquals(List.of(), checker.getAvailableProviders());
    }

    @Test
    void googleNotAvailable_whenOnlyClientSecretPresent() {
        // given
        var checker = new OAuth2AvailabilityChecker(
                createSecurityProperties(false), "", "google-client-secret", "", "", "", "", "", "");

        // then
        assertFalse(checker.isOAuth2Available());
        assertEquals(List.of(), checker.getAvailableProviders());
    }

    @Test
    void azureAvailable_whenAllThreeCredentialsPresent() {
        // given
        var checker = new OAuth2AvailabilityChecker(
                createSecurityProperties(false),
                "",
                "",
                "azure-client-id",
                "azure-client-secret",
                "azure-tenant-id",
                "",
                "",
                "");

        // then
        assertTrue(checker.isOAuth2Available());
        assertEquals(List.of("azure"), checker.getAvailableProviders());
    }

    @Test
    void azureNotAvailable_whenMissingTenantId() {
        // given
        var checker = new OAuth2AvailabilityChecker(
                createSecurityProperties(false), "", "", "azure-client-id", "azure-client-secret", "", "", "", "");

        // then
        assertFalse(checker.isOAuth2Available());
        assertEquals(List.of(), checker.getAvailableProviders());
    }

    @Test
    void azureNotAvailable_whenMissingClientId() {
        // given
        var checker = new OAuth2AvailabilityChecker(
                createSecurityProperties(false), "", "", "", "azure-client-secret", "azure-tenant-id", "", "", "");

        // then
        assertFalse(checker.isOAuth2Available());
        assertEquals(List.of(), checker.getAvailableProviders());
    }

    @Test
    void azureNotAvailable_whenMissingClientSecret() {
        // given
        var checker = new OAuth2AvailabilityChecker(
                createSecurityProperties(false), "", "", "azure-client-id", "", "azure-tenant-id", "", "", "");

        // then
        assertFalse(checker.isOAuth2Available());
        assertEquals(List.of(), checker.getAvailableProviders());
    }

    @Test
    void bothProvidersAvailable_whenFullyConfigured() {
        // given
        var checker = new OAuth2AvailabilityChecker(
                createSecurityProperties(false),
                "google-client-id",
                "google-client-secret",
                "azure-client-id",
                "azure-client-secret",
                "azure-tenant-id",
                "",
                "",
                "");

        // then
        assertTrue(checker.isOAuth2Available());
        assertEquals(List.of("google", "azure"), checker.getAvailableProviders());
    }

    @Test
    void noProvidersAvailable_whenNoCredentialsConfigured() {
        // given
        var checker = new OAuth2AvailabilityChecker(createSecurityProperties(false), "", "", "", "", "", "", "", "");

        // then
        assertFalse(checker.isOAuth2Available());
        assertEquals(List.of(), checker.getAvailableProviders());
    }

    @Test
    void whitespaceStrings_treatedAsBlank() {
        // given
        var checker = new OAuth2AvailabilityChecker(
                createSecurityProperties(false), "   ", "  \t  ", " \n ", "", "", "", "", "");

        // then
        assertFalse(checker.isOAuth2Available());
        assertEquals(List.of(), checker.getAvailableProviders());
    }

    @Test
    void dexAvailable_whenAllThreeCredentialsPresent() {
        // given
        var checker = new OAuth2AvailabilityChecker(
                createSecurityProperties(false),
                "",
                "",
                "",
                "",
                "",
                "dex-client-id",
                "dex-client-secret",
                "https://dex.example.com");
        // then
        assertTrue(checker.isOAuth2Available());
        assertEquals(List.of("dex"), checker.getAvailableProviders());
    }

    @Test
    void dexNotAvailable_whenMissingIssuerUri() {
        // given
        var checker = new OAuth2AvailabilityChecker(
                createSecurityProperties(false), "", "", "", "", "", "dex-client-id", "dex-client-secret", "");

        // then
        assertFalse(checker.isOAuth2Available());
        assertEquals(List.of(), checker.getAvailableProviders());
    }

    @Test
    void loginProvidersAllowlist_filtersToDex_whenGoogleAlsoConfigured() {
        var checker = new OAuth2AvailabilityChecker(
                createSecurityProperties(false, List.of("dex")),
                "google-client-id",
                "google-client-secret",
                "",
                "",
                "",
                "dex-client-id",
                "dex-client-secret",
                "https://dex.example.com");

        assertTrue(checker.isOAuth2Available());
        assertEquals(List.of("dex"), checker.getAvailableProviders());
    }

    @Test
    void loginProvidersAllowlist_excludesDex_whenOnlyDexConfiguredButAllowlistGoogle() {
        var checker = new OAuth2AvailabilityChecker(
                createSecurityProperties(false, List.of("google")),
                "",
                "",
                "",
                "",
                "",
                "dex-client-id",
                "dex-client-secret",
                "https://dex.example.com");

        assertFalse(checker.isOAuth2Available());
        assertEquals(List.of(), checker.getAvailableProviders());
    }

    @Test
    void loginProvidersAllowlist_isCaseInsensitive() {
        var checker = new OAuth2AvailabilityChecker(
                createSecurityProperties(false, List.of("DEX")),
                "",
                "",
                "",
                "",
                "",
                "dex-client-id",
                "dex-client-secret",
                "https://dex.example.com");

        assertTrue(checker.isOAuth2Available());
        assertEquals(List.of("dex"), checker.getAvailableProviders());
    }
}
