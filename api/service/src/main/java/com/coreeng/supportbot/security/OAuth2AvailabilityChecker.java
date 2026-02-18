package com.coreeng.supportbot.security;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Detects which OAuth2 providers are fully configured and available for authentication.
 * A provider is only considered available if ALL required credentials are present:
 * - Google: client-id AND client-secret
 * - Azure: client-id AND client-secret AND tenant-id
 */
@Slf4j
@Component
public class OAuth2AvailabilityChecker {
    private final boolean testBypassEnabled;
    private final boolean oauth2Available;
    private final List<String> availableProviders;

    public OAuth2AvailabilityChecker(
            SecurityProperties securityProperties,
            @Value("${spring.security.oauth2.client.registration.google.client-id:}") String googleClientId,
            @Value("${spring.security.oauth2.client.registration.google.client-secret:}") String googleClientSecret,
            @Value("${spring.security.oauth2.client.registration.azure.client-id:}") String azureClientId,
            @Value("${spring.security.oauth2.client.registration.azure.client-secret:}") String azureClientSecret,
            @Value("${spring.security.oauth2.client.provider.azure.tenant-id:}") String azureTenantId) {
        this.testBypassEnabled = securityProperties.testBypass() != null
                && securityProperties.testBypass().enabled();

        // Detect available providers - all credentials must be present for a provider to be available
        var providers = new ArrayList<String>();
        if (isNotBlank(googleClientId) && isNotBlank(googleClientSecret)) {
            providers.add("google");
        }
        if (isNotBlank(azureClientId) && isNotBlank(azureClientSecret) && isNotBlank(azureTenantId)) {
            providers.add("azure");
        }

        // Store immutable copy to prevent accidental modification
        this.availableProviders = List.copyOf(providers);
        this.oauth2Available = !this.availableProviders.isEmpty();
    }

    public boolean isOAuth2Available() {
        return oauth2Available;
    }

    /**
     * Returns the list of fully configured OAuth2 providers.
     * @return Immutable list of provider names (e.g., "google", "azure")
     */
    public List<String> getAvailableProviders() {
        return availableProviders;
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
                    + "Set GOOGLE_CLIENT_ID/GOOGLE_CLIENT_SECRET or all three of "
                    + "AZURE_CLIENT_ID/AZURE_CLIENT_SECRET/AZURE_TENANT_ID, "
                    + "or enable security.test-bypass.enabled for testing.");
        }
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
