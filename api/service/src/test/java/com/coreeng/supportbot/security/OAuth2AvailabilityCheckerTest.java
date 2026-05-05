package com.coreeng.supportbot.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        assertThat(checker.isOAuth2Available()).isTrue();
    }

    @Test
    void dexNotAvailable_whenMissingIssuerUri() {
        var checker = new OAuth2AvailabilityChecker(
                createSecurityProperties(false), "dex-client-id", "dex-client-secret", "");

        assertThat(checker.isOAuth2Available()).isFalse();
    }

    @Test
    void dexNotAvailable_whenMissingClientId() {
        var checker = new OAuth2AvailabilityChecker(
                createSecurityProperties(false), "", "dex-client-secret", "https://dex.example.com");

        assertThat(checker.isOAuth2Available()).isFalse();
    }

    @Test
    void dexNotAvailable_whenMissingClientSecret() {
        var checker = new OAuth2AvailabilityChecker(
                createSecurityProperties(false), "dex-client-id", "", "https://dex.example.com");

        assertThat(checker.isOAuth2Available()).isFalse();
    }

    @Test
    void whitespaceCredentials_treatedAsBlank() {
        var checker = new OAuth2AvailabilityChecker(createSecurityProperties(false), "   ", "  \t  ", " \n ");

        assertThat(checker.isOAuth2Available()).isFalse();
    }

    @Test
    void checkOAuth2Configuration_throws_whenDexMissingAndTestBypassDisabled() {
        var checker = new OAuth2AvailabilityChecker(createSecurityProperties(false), "", "", "");

        assertThatThrownBy(checker::checkOAuth2Configuration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEX_CLIENT_ID")
                .hasMessageContaining("test-bypass");
    }

    @Test
    void checkOAuth2Configuration_doesNotThrow_whenDexConfigured() {
        var checker = new OAuth2AvailabilityChecker(
                createSecurityProperties(false), "dex-client-id", "dex-client-secret", "https://dex.example.com");

        assertThatCode(checker::checkOAuth2Configuration).doesNotThrowAnyException();
    }

    @Test
    void checkOAuth2Configuration_doesNotThrow_whenTestBypassEnabled() {
        var checker = new OAuth2AvailabilityChecker(createSecurityProperties(true), "", "", "");

        assertThatCode(checker::checkOAuth2Configuration).doesNotThrowAnyException();
    }

    @Test
    void checkOAuth2Configuration_doesNotThrow_whenBothDexAndTestBypassPresent() {
        var checker = new OAuth2AvailabilityChecker(
                createSecurityProperties(true), "dex-client-id", "dex-client-secret", "https://dex.example.com");

        assertThatCode(checker::checkOAuth2Configuration).doesNotThrowAnyException();
    }
}
