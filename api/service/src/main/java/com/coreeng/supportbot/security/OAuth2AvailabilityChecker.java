package com.coreeng.supportbot.security;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Detects whether Dex is fully configured and available for authentication.
 * Dex is the only OAuth2 client this app registers; it is considered available
 * only when client-id, client-secret, and issuer-uri are all present.
 */
@Slf4j
@Component
public class OAuth2AvailabilityChecker {
    private final boolean testBypassEnabled;
    private final List<String> availableProviders;

    public OAuth2AvailabilityChecker(
            SecurityProperties securityProperties,
            @Value("${spring.security.oauth2.client.registration.dex.client-id:}") String dexClientId,
            @Value("${spring.security.oauth2.client.registration.dex.client-secret:}") String dexClientSecret,
            @Value("${spring.security.oauth2.client.provider.dex.issuer-uri:}") String dexIssuerUri) {
        this.testBypassEnabled = securityProperties.testBypass() != null
                && securityProperties.testBypass().enabled();

        this.availableProviders = isNotBlank(dexClientId) && isNotBlank(dexClientSecret) && isNotBlank(dexIssuerUri)
                ? List.of("dex")
                : List.of();
    }

    public boolean isOAuth2Available() {
        return !availableProviders.isEmpty();
    }

    /**
     * Returns the list of fully configured OAuth2 providers (only ever {@code [dex]} or empty).
     */
    public List<String> getAvailableProviders() {
        return availableProviders;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void checkOAuth2Configuration() {
        if (isOAuth2Available()) {
            log.info("OAuth2 authentication configured. Active login providers: {}", availableProviders);
        } else if (testBypassEnabled) {
            log.info("OAuth2 credentials not configured, but test-bypass is enabled. "
                    + "Authentication will use X-Test-User/X-Test-Role headers.");
        } else {
            log.warn("OAuth2 credentials not configured and test-bypass is disabled. "
                    + "Users will not be able to authenticate. "
                    + "Set DEX_CLIENT_ID/DEX_CLIENT_SECRET/DEX_ISSUER_URI, "
                    + "or enable security.test-bypass.enabled for testing.");
        }
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
