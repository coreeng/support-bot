package com.coreeng.supportbot.security;

import java.net.URI;
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
            uri = URI.create(redirectUri).normalize();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Malformed redirect_uri", e);
        }

        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalArgumentException("redirect_uri must have an absolute scheme and host");
        }

        String origin = originOf(uri);
        if (!allowedOrigin.equals(origin)) {
            log.warn("redirect_uri origin mismatch: expected={}, got={}", allowedOrigin, origin);
            throw new IllegalArgumentException("redirect_uri origin not allowed");
        }

        String path = uri.getPath();
        if (path == null || !path.startsWith(OauthUiCallbackConstants.CALLBACK_PATH_PREFIX)) {
            throw new IllegalArgumentException(
                    "redirect_uri path must start with " + OauthUiCallbackConstants.CALLBACK_PATH_PREFIX);
        }
        String provider = path.substring(OauthUiCallbackConstants.CALLBACK_PATH_PREFIX.length());
        if (!OauthUiCallbackConstants.KNOWN_PROVIDERS.contains(provider)) {
            throw new IllegalArgumentException("redirect_uri references unknown provider: " + provider);
        }
    }

    private static String extractOrigin(String uriString) {
        if (uriString == null || uriString.isBlank()) {
            throw new IllegalStateException(
                    "security.oauth2.redirect-uri must be configured with a valid absolute URI");
        }
        try {
            URI uri = URI.create(uriString);
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new IllegalStateException(
                        "security.oauth2.redirect-uri must have a scheme and host, got: " + uriString);
            }
            return originOf(uri);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("security.oauth2.redirect-uri is not a valid URI: " + uriString, e);
        }
    }

    private static String originOf(URI uri) {
        String scheme = uri.getScheme();
        String host = uri.getHost();
        int port = uri.getPort();
        boolean defaultPort =
                (port == -1) || ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        return defaultPort ? scheme + "://" + host : scheme + "://" + host + ":" + port;
    }
}
