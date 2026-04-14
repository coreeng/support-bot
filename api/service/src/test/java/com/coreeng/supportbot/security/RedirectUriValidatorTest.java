package com.coreeng.supportbot.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class RedirectUriValidatorTest {

    private RedirectUriValidator validator(String configuredRedirectUri) {
        var props = new SecurityProperties(
                new SecurityProperties.JwtProperties(
                        "secret-not-used-here-but-must-be-256-bits!!", Duration.ofHours(1)),
                SecurityProperties.OAuth2Properties.withRedirectOnly(configuredRedirectUri),
                new SecurityProperties.CorsProperties(null),
                new SecurityProperties.TestBypassProperties(false),
                new SecurityProperties.AllowListProperties(List.of(), List.of()));
        return new RedirectUriValidator(props);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http://localhost:3000/api/oauth/callback/google",
                "http://localhost:3000/api/oauth/callback/azure",
                "http://localhost:3000/api/oauth/callback/dex",
            })
    void acceptsValidCallbackUris(String uri) {
        var v = validator("http://localhost:3000/login");
        assertDoesNotThrow(() -> v.validate(uri));
    }

    @Test
    void acceptsHttpsOrigin() {
        var v = validator("https://app.example.com/login");
        assertDoesNotThrow(() -> v.validate("https://app.example.com/api/oauth/callback/dex"));
    }

    @Test
    void acceptsNonDefaultPort() {
        var v = validator("https://app.example.com:8443/login");
        assertDoesNotThrow(() -> v.validate("https://app.example.com:8443/api/oauth/callback/google"));
    }

    @Test
    void rejectsWrongHost() {
        var v = validator("http://localhost:3000/login");
        assertThrows(IllegalArgumentException.class, () -> v.validate("http://evil.example/api/oauth/callback/google"));
    }

    @Test
    void rejectsWrongPort() {
        var v = validator("http://localhost:3000/login");
        assertThrows(
                IllegalArgumentException.class, () -> v.validate("http://localhost:9999/api/oauth/callback/google"));
    }

    @Test
    void rejectsWrongScheme() {
        var v = validator("https://app.example.com/login");
        assertThrows(
                IllegalArgumentException.class, () -> v.validate("http://app.example.com/api/oauth/callback/google"));
    }

    @Test
    void rejectsWrongPath() {
        var v = validator("http://localhost:3000/login");
        assertThrows(IllegalArgumentException.class, () -> v.validate("http://localhost:3000/some/other/path"));
    }

    @Test
    void rejectsUnknownProvider() {
        var v = validator("http://localhost:3000/login");
        assertThrows(IllegalArgumentException.class, () -> v.validate("http://localhost:3000/api/oauth/callback/evil"));
    }

    @Test
    void rejectsMalformedUri() {
        var v = validator("http://localhost:3000/login");
        assertThrows(IllegalArgumentException.class, () -> v.validate("not a uri"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "not-a-uri", "//missing-scheme.example.com/path"})
    void failsAtConstructionWithInvalidConfig(String configuredUri) {
        assertThrows(IllegalStateException.class, () -> validator(configuredUri));
    }

    @Test
    void rejectsSchemeRelativeUri() {
        var v = validator("http://localhost:3000/login");
        assertThrows(IllegalArgumentException.class, () -> v.validate("//attacker.example/api/oauth/callback/dex"));
    }
}
