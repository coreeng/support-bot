package com.coreeng.supportbot.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OAuth2ClientConfigTest {

    private final OAuth2ClientConfig config = new OAuth2ClientConfig();

    @Test
    void dexRegistrationCreated_whenAllDexFieldsPresent() {
        var repository = config.clientRegistrationRepository(
                "dex-client-id", "dex-client-secret", "openid,email,profile,groups", "https://dex.example.com", "");

        var dex = repository.findByRegistrationId("dex");
        assertNotNull(dex);
        assertTrue(dex.getScopes().contains("openid"));
        assertTrue(dex.getScopes().contains("groups"));
    }

    @Test
    void dexRegistrationUsesConfiguredScopes() {
        var repository = config.clientRegistrationRepository(
                "dex-client-id", "dex-client-secret", "openid, profile , custom-scope", "https://dex.example.com", "");

        var dex = repository.findByRegistrationId("dex");
        assertNotNull(dex);
        assertTrue(dex.getScopes().contains("openid"));
        assertTrue(dex.getScopes().contains("profile"));
        assertTrue(dex.getScopes().contains("custom-scope"));
        assertFalse(dex.getScopes().contains("groups"));
    }

    @Test
    void dexRegistrationNotCreated_whenMissingIssuer() {
        var repository = config.clientRegistrationRepository(
                "dex-client-id", "dex-client-secret", "openid,email,profile,groups", "", "");

        assertNull(repository.findByRegistrationId("dex"));
    }

    @Test
    void dexRegistrationNotCreated_whenMissingClientId() {
        var repository = config.clientRegistrationRepository(
                "", "dex-client-secret", "openid,email,profile,groups", "https://dex.example.com", "");

        assertNull(repository.findByRegistrationId("dex"));
    }

    @Test
    void dexRegistrationNotCreated_whenMissingClientSecret() {
        var repository = config.clientRegistrationRepository(
                "dex-client-id", "", "openid,email,profile,groups", "https://dex.example.com", "");

        assertNull(repository.findByRegistrationId("dex"));
    }

    @Test
    void dexRegistrationUsesInternalBaseUrl_whenValid() {
        var repository = config.clientRegistrationRepository(
                "dex-client-id",
                "dex-client-secret",
                "openid,email,profile,groups",
                "https://dex.example.com",
                "http://dex.my-namespace.svc.cluster.local:5556");

        var dex = repository.findByRegistrationId("dex");
        assertNotNull(dex);
        assertEquals(
                "http://dex.my-namespace.svc.cluster.local:5556/token",
                dex.getProviderDetails().getTokenUri());
        assertEquals(
                "http://dex.my-namespace.svc.cluster.local:5556/keys",
                dex.getProviderDetails().getJwkSetUri());
    }

    @Test
    void dexRegistrationRejectsInternalBaseUrl_withBareHostname() {
        assertThrows(
                IllegalArgumentException.class,
                () -> config.clientRegistrationRepository(
                        "dex-client-id",
                        "dex-client-secret",
                        "openid,email,profile,groups",
                        "https://dex.example.com",
                        "http://dex:5556"));
    }

    @Test
    void dexRegistrationRejectsInternalBaseUrl_withBadScheme() {
        assertThrows(
                IllegalArgumentException.class,
                () -> config.clientRegistrationRepository(
                        "dex-client-id",
                        "dex-client-secret",
                        "openid,email,profile,groups",
                        "https://dex.example.com",
                        "ftp://dex:5556"));
    }
}
