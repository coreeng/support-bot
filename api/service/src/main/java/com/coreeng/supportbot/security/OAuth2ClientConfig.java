package com.coreeng.supportbot.security;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;

@Slf4j
@Configuration
public class OAuth2ClientConfig {

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(
            @Value("${spring.security.oauth2.client.registration.dex.client-id:}") String dexClientId,
            @Value("${spring.security.oauth2.client.registration.dex.client-secret:}") String dexClientSecret,
            @Value("${spring.security.oauth2.client.registration.dex.scope:openid,email,profile,groups}")
                    String dexScope,
            @Value("${spring.security.oauth2.client.provider.dex.issuer-uri:}") String dexIssuerUri,
            @Value("${spring.security.oauth2.client.provider.dex.internal-base-url:}") String dexInternalBaseUrl) {
        if (isNotBlank(dexClientId) && isNotBlank(dexClientSecret) && isNotBlank(dexIssuerUri)) {
            log.info("Dex OAuth2 client registered");
            return new InMemoryClientRegistrationRepository(List.of(
                    dexClientRegistration(dexClientId, dexClientSecret, dexIssuerUri, dexScope, dexInternalBaseUrl)));
        }

        log.info("No OAuth2 clients registered - Dex credentials not provided");
        return registrationId -> null;
    }

    private ClientRegistration dexClientRegistration(
            String clientId, String clientSecret, String issuerUri, String scopeProperty, String internalBaseUrl) {
        String normalizedIssuerUri =
                issuerUri.endsWith("/") ? issuerUri.substring(0, issuerUri.length() - 1) : issuerUri;
        // Browser redirects use the external ingress URL; server-to-server calls
        // (/token, /userinfo, /keys) use the in-cluster URL when provided to bypass IAP.
        String validatedInternalUrl = validateInternalBaseUrl(internalBaseUrl);
        String serverBaseUrl = validatedInternalUrl != null ? validatedInternalUrl : normalizedIssuerUri;
        if (!serverBaseUrl.equals(normalizedIssuerUri)) {
            log.debug("Dex server-to-server calls will use internal URL: {}", serverBaseUrl);
        }
        return ClientRegistration.withRegistrationId("dex")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope(dexScopesFromProperty(scopeProperty))
                .issuerUri(normalizedIssuerUri)
                .authorizationUri(normalizedIssuerUri + "/auth")
                .tokenUri(serverBaseUrl + "/token")
                .jwkSetUri(serverBaseUrl + "/keys")
                .userInfoUri(serverBaseUrl + "/userinfo")
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .clientName("Dex")
                .build();
    }

    private static final Set<String> ALLOWED_INTERNAL_SCHEMES = Set.of("http", "https");

    /**
     * Validates dexInternalBaseUrl: the client_secret is sent to this URL, so a misconfigured value
     * (typo, attacker-controlled) would exfiltrate it. We enforce scheme and warn on non-cluster hosts.
     *
     * @return the normalized URL (no trailing slash), or {@code null} if blank/invalid
     */
    @org.jspecify.annotations.Nullable private static String validateInternalBaseUrl(String internalBaseUrl) {
        if (!isNotBlank(internalBaseUrl)) {
            return null;
        }
        String normalized = internalBaseUrl.endsWith("/")
                ? internalBaseUrl.substring(0, internalBaseUrl.length() - 1)
                : internalBaseUrl;
        try {
            URI uri = URI.create(normalized);
            if (uri.getScheme() == null || !ALLOWED_INTERNAL_SCHEMES.contains(uri.getScheme())) {
                throw new IllegalArgumentException(
                        "dex internal-base-url must use http or https scheme, got: " + uri.getScheme());
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException("dex internal-base-url has no host: " + normalized);
            }
            String host = uri.getHost();
            if (!host.equals("localhost") && !host.equals("127.0.0.1") && !host.endsWith(".svc.cluster.local")) {
                throw new IllegalArgumentException(
                        "dex internal-base-url host must be localhost, 127.0.0.1, or end with"
                                + " .svc.cluster.local to prevent leaking the client secret to an"
                                + " external host, got: "
                                + host);
            }
            return normalized;
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if (msg != null && msg.startsWith("dex internal-base-url")) {
                throw e;
            }
            throw new IllegalArgumentException("dex internal-base-url is not a valid URI: " + normalized, e);
        }
    }

    /**
     * Same comma-separated format as {@code spring.security.oauth2.client.registration.dex.scope} /
     * {@code DEX_SCOPES}.
     */
    private static String[] dexScopesFromProperty(String scopeProperty) {
        if (scopeProperty == null || scopeProperty.isBlank()) {
            return new String[] {"openid", "email", "profile", "groups"};
        }
        String[] parsed = Arrays.stream(scopeProperty.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        return parsed.length > 0 ? parsed : new String[] {"openid", "email", "profile", "groups"};
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
