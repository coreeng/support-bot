package com.coreeng.supportbot.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Detects whether Dex is fully configured. Dex is the only OAuth2 client this app registers; it is
 * considered available only when client-id, client-secret, and issuer-uri are all present.
 *
 * <p>At {@link ApplicationReadyEvent} time, fails startup with {@link IllegalStateException} if Dex
 * is not configured and {@code security.test-bypass.enabled} is false. Test-bypass is the
 * documented escape hatch for the functionaltests / integrationtests / nft profiles that don't
 * stand up real OAuth.
 */
@Slf4j
@Component
public class OAuth2AvailabilityChecker {
    private final boolean testBypassEnabled;
    private final boolean dexConfigured;

    public OAuth2AvailabilityChecker(
            SecurityProperties securityProperties,
            @Value("${spring.security.oauth2.client.registration.dex.client-id:}") String dexClientId,
            @Value("${spring.security.oauth2.client.registration.dex.client-secret:}") String dexClientSecret,
            @Value("${spring.security.oauth2.client.provider.dex.issuer-uri:}") String dexIssuerUri) {
        this.testBypassEnabled = securityProperties.testBypass() != null
                && securityProperties.testBypass().enabled();

        this.dexConfigured = isNotBlank(dexClientId) && isNotBlank(dexClientSecret) && isNotBlank(dexIssuerUri);
    }

    public boolean isOAuth2Available() {
        return dexConfigured;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void checkOAuth2Configuration() {
        if (dexConfigured) {
            log.info("Dex OAuth2 client configured.");
            return;
        }
        if (testBypassEnabled) {
            log.info("Dex OAuth2 not configured, but security.test-bypass.enabled=true. "
                    + "Authentication will use X-Test-User/X-Test-Role headers.");
            return;
        }
        String message = "Dex OAuth2 client is not configured. "
                + "Set DEX_CLIENT_ID, DEX_CLIENT_SECRET, and DEX_ISSUER_URI, "
                + "or set security.test-bypass.enabled=true for tests/local without auth. "
                + "Refusing to start without a usable authentication path.";
        log.error(message);
        throw new IllegalStateException(message);
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
