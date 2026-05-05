package com.coreeng.supportbot.security;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Validates OAuth {@code redirect_uri} values against the configured UI origin so the API never
 * forwards an attacker-controlled URI (and the embedded {@code client_secret}) to an arbitrary host.
 *
 * <p>The allowed origin is derived from {@code security.oauth2.redirect-uri} (the fixed post-login
 * redirect target). Only the scheme + authority (host:port) are compared; the path must be exactly
 * {@code /api/oauth/callback/dex}. Userinfo, fragments, and ambiguous host forms are rejected.
 */
@Slf4j
@Component
public class RedirectUriValidator {
    private static final String ALLOWED_PATH = "/api/oauth/callback/dex";

    private final String allowedOrigin;

    public RedirectUriValidator(SecurityProperties properties) {
        this.allowedOrigin = extractOrigin(properties.oauth2().redirectUri());
    }

    /**
     * @throws IllegalArgumentException if the URI is malformed, targets an unknown origin, or has
     *     an unexpected path
     */
    public ValidatedRedirectUri validate(String redirectUri) {
        URI uri;
        try {
            uri = URI.create(redirectUri).normalize();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Malformed redirect_uri", e);
        }

        if (uri.getRawUserInfo() != null && !uri.getRawUserInfo().isEmpty()) {
            throw new IllegalArgumentException("redirect_uri must not contain userinfo");
        }
        if (uri.getRawFragment() != null && !uri.getRawFragment().isEmpty()) {
            throw new IllegalArgumentException("redirect_uri must not contain a fragment");
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
        if (!ALLOWED_PATH.equals(path)) {
            throw new IllegalArgumentException("redirect_uri path must be " + ALLOWED_PATH);
        }

        return new ValidatedRedirectUri.Valid(canonicalRedirectUri(uri));
    }

    private static String canonicalRedirectUri(URI in) {
        try {
            URI rebuilt = new URI(in.getScheme(), null, in.getHost(), in.getPort(), in.getPath(), in.getQuery(), null);
            return rebuilt.normalize().toASCIIString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid redirect_uri components", e);
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
        String host = asciiHost(uri.getHost());
        int port = uri.getPort();
        boolean defaultPort =
                (port == -1) || ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        return defaultPort ? scheme + "://" + host : scheme + "://" + host + ":" + port;
    }

    private static String asciiHost(String host) {
        try {
            return IDN.toASCII(host);
        } catch (IllegalArgumentException e) {
            return host;
        }
    }
}
