package com.coreeng.supportbot.security;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OAuth2ClientConfigTest {

    private final OAuth2ClientConfig config = new OAuth2ClientConfig();

    @Test
    void dexRegistrationCreated_whenAllDexFieldsPresent() {
        var repository = config.clientRegistrationRepository(
                "", "", "", "", "", "dex-client-id", "dex-client-secret", "https://dex.example.com");

        var dex = repository.findByRegistrationId("dex");
        assertNotNull(dex);
        assertTrue(dex.getScopes().contains("openid"));
        assertTrue(dex.getScopes().contains("groups"));
    }

    @Test
    void dexRegistrationNotCreated_whenMissingIssuer() {
        var repository =
                config.clientRegistrationRepository("", "", "", "", "", "dex-client-id", "dex-client-secret", "");

        assertNull(repository.findByRegistrationId("dex"));
    }
}
