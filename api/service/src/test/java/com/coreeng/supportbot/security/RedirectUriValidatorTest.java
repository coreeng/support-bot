package com.coreeng.supportbot.security;

import static org.assertj.core.api.Assertions.assertThat;
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
                new SecurityProperties.OAuth2Properties(configuredRedirectUri),
                new SecurityProperties.CorsProperties(null),
                new SecurityProperties.TestBypassProperties(false),
                new SecurityProperties.AllowListProperties(List.of(), List.of()));
        return new RedirectUriValidator(props);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:3000/api/oauth/callback/dex"})
    void acceptsValidCallbackUris(String uri) {
        var v = validator("http://localhost:3000/login");
        ValidatedRedirectUri validated = assertDoesNotThrow(() -> v.validate(uri));
        assertThat(validated.value()).isEqualTo(uri);
    }

    @Test
    void returnsValidatedRedirectUriWithCanonicalAsciiValue() {
        var v = validator("http://localhost:3000/login");
        var validated = v.validate("http://localhost:3000/api/oauth/callback/dex");
        assertThat(validated).isInstanceOf(ValidatedRedirectUri.class);
        assertThat(validated.value()).isEqualTo("http://localhost:3000/api/oauth/callback/dex");
    }

    @Test
    void acceptsHttpsOrigin() {
        var v = validator("https://app.example.com/login");
        var validated = assertDoesNotThrow(() -> v.validate("https://app.example.com/api/oauth/callback/dex"));
        assertThat(validated.value()).isEqualTo("https://app.example.com/api/oauth/callback/dex");
    }

    @Test
    void acceptsNonDefaultPort() {
        var v = validator("https://app.example.com:8443/login");
        var validated = assertDoesNotThrow(() -> v.validate("https://app.example.com:8443/api/oauth/callback/dex"));
        assertThat(validated.value()).isEqualTo("https://app.example.com:8443/api/oauth/callback/dex");
    }

    @Test
    void rejectsWrongHost() {
        var v = validator("http://localhost:3000/login");
        assertThrows(IllegalArgumentException.class, () -> v.validate("http://evil.example/api/oauth/callback/dex"));
    }

    @Test
    void rejectsWrongPort() {
        var v = validator("http://localhost:3000/login");
        assertThrows(IllegalArgumentException.class, () -> v.validate("http://localhost:9999/api/oauth/callback/dex"));
    }

    @Test
    void rejectsWrongScheme() {
        var v = validator("https://app.example.com/login");
        assertThrows(IllegalArgumentException.class, () -> v.validate("http://app.example.com/api/oauth/callback/dex"));
    }

    @Test
    void rejectsWrongPath() {
        var v = validator("http://localhost:3000/login");
        assertThrows(IllegalArgumentException.class, () -> v.validate("http://localhost:3000/some/other/path"));
    }

    @Test
    void rejectsNonDexProviderPath() {
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

    /** Userinfo before {@code @} must not bypass origin checks (host is still allowed UI host). */
    @Test
    void rejectsUserinfoWhenHostMatchesAllowedOrigin() {
        var v = validator("http://localhost:3000/login");
        var ex = assertThrows(
                IllegalArgumentException.class,
                () -> v.validate("http://attacker@localhost:3000/api/oauth/callback/dex"));
        assertThat(ex.getMessage()).containsIgnoringCase("userinfo");
    }

    /** Defense in depth: authority {@code localhost:3000@evil.example} yields a non-allowed host. */
    @Test
    void rejectsUserinfoDisguisedAsPort() {
        var v = validator("http://localhost:3000/login");
        assertThrows(
                IllegalArgumentException.class,
                () -> v.validate("http://localhost:3000@evil.example/api/oauth/callback/dex"));
    }

    /** Unicode “dot” in host breaks hostname parsing — must not be accepted as localhost. */
    @Test
    void rejectsUnicodeHomographHost() {
        var v = validator("http://localhost:3000/login");
        String malicious = "http://localhost\u3002example.com:3000/api/oauth/callback/dex";
        assertThrows(IllegalArgumentException.class, () -> v.validate(malicious));
    }

    @Test
    void rejectsTrailingSlashAfterProvider() {
        var v = validator("http://localhost:3000/login");
        assertThrows(IllegalArgumentException.class, () -> v.validate("http://localhost:3000/api/oauth/callback/dex/"));
    }

    @Test
    void rejectsFragment() {
        var v = validator("http://localhost:3000/login");
        var ex = assertThrows(
                IllegalArgumentException.class,
                () -> v.validate("http://localhost:3000/api/oauth/callback/dex#@evil.com"));
        assertThat(ex.getMessage()).containsIgnoringCase("fragment");
    }
}
