package com.coreeng.supportbot.security;

import java.net.URI;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Validates OAuth {@code redirect_uri} values against the configured UI origin so the API never
 * forwards an attacker-controlled URI (and the embedded {@code client_secret}) to an arbitrary host.
 *
 * <p>The allowed origin is derived from {@code security.oauth2.redirect-uri} (the fixed post-login
 * redirect target). Only the scheme + authority (host:port) are compared; the path must begin with
 * {@code /api/oauth/callback/} followed by a known provider.
 */
@Slf4j
@Component
public class RedirectUriValidator {
    private static final Set<String> KNOWN_PROVIDERS = Set.of("google", "azure", "dex");
    private static final String CALLBACK_PATH_PREFIX = "/api/oauth/callback/";

    private final String allowedOrigin;

    public RedirectUriValidator(SecurityProperties properties) {
        this.allowedOrigin = extractOrigin(properties.oauth2().redirectUri());
    }

    /**
     * @throws IllegalArgumentException if the URI is malformed, targets an unknown origin, or has
     *     an unexpected path
     */
    public void validate(String redirectUri) {
        URI uri;
        try {
            uri = URI.create(redirectUri);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Malformed redirect_uri", e);
        }

        String origin = originOf(uri);
        if (!allowedOrigin.equals(origin)) {
            log.warn("redirect_uri origin mismatch: expected={}, got={}", allowedOrigin, origin);
            throw new IllegalArgumentException("redirect_uri origin not allowed");
        }

        String path = uri.getPath();
        if (path == null || !path.startsWith(CALLBACK_PATH_PREFIX)) {
            throw new IllegalArgumentException("redirect_uri path must start with " + CALLBACK_PATH_PREFIX);
        }
        String provider = path.substring(CALLBACK_PATH_PREFIX.length());
        if (!KNOWN_PROVIDERS.contains(provider)) {
            throw new IllegalArgumentException("redirect_uri references unknown provider: " + provider);
        }
    }

    private static String extractOrigin(String uriString) {
        if (uriString == null || uriString.isBlank()) {
            return "";
        }
        try {
            return originOf(URI.create(uriString));
        } catch (IllegalArgumentException e) {
            log.warn("Could not parse security.oauth2.redirect-uri for origin extraction: {}", uriString);
            return "";
        }
    }

    private static String originOf(URI uri) {
        String scheme = uri.getScheme();
        String host = uri.getHost();
        int port = uri.getPort();
        if (scheme == null || host == null) {
            return "";
        }
        boolean defaultPort = (port == -1)
                || ("http".equals(scheme) && port == 80)
                || ("https".equals(scheme) && port == 443);
        return defaultPort ? scheme + "://" + host : scheme + "://" + host + ":" + port;
    }
}
