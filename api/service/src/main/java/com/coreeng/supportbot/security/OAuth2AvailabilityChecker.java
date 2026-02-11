package com.coreeng.supportbot.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OAuth2AvailabilityChecker {
    private final boolean testBypassEnabled;
    private final boolean oauth2Available;

    public OAuth2AvailabilityChecker(
            SecurityProperties securityProperties,
            @Value("${spring.security.oauth2.client.registration.google.client-id:}") String googleClientId,
            @Value("${spring.security.oauth2.client.registration.azure.client-id:}") String azureClientId) {
        this.testBypassEnabled = securityProperties.testBypass() != null
                && securityProperties.testBypass().enabled();
        this.oauth2Available = isNotBlank(googleClientId) || isNotBlank(azureClientId);
    }

    public boolean isOAuth2Available() {
        return oauth2Available;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void checkOAuth2Configuration() {
        if (oauth2Available) {
            log.info("OAuth2 authentication configured and available.");
        } else if (testBypassEnabled) {
            log.info("OAuth2 credentials not configured, but test-bypass is enabled. "
                    + "Authentication will use X-Test-User/X-Test-Role headers.");
        } else {
            log.warn("OAuth2 credentials not configured and test-bypass is disabled. "
                    + "Users will not be able to authenticate. "
                    + "Set GOOGLE_CLIENT_ID/GOOGLE_CLIENT_SECRET or AZURE_CLIENT_ID/AZURE_CLIENT_SECRET, "
                    + "or enable security.test-bypass.enabled for testing.");
        }
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
