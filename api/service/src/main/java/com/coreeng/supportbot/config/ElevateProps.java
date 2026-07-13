package com.coreeng.supportbot.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "elevate")
public record ElevateProps(
        @DefaultValue("") String baseUrl,
        @DefaultValue("") String clientId,
        @DefaultValue("") String clientSecret,
        @DefaultValue("1h") Duration statusInterval,
        @DefaultValue("12h") Duration syncInterval,
        @DefaultValue("Support Bot") String agentName,
        @DefaultValue("http://localhost:3000") String supportBotUrl,
        @DefaultValue("dev") String version) {

    public ElevateProps {
        baseUrl = normalizeBaseUrl(baseUrl);
        clientId = clientId.trim();
        clientSecret = clientSecret.trim();
        agentName = agentName.trim();
        supportBotUrl = supportBotUrl.trim();
        version = version.trim();

        boolean anyConnectionValue = !baseUrl.isEmpty() || !clientId.isEmpty() || !clientSecret.isEmpty();
        boolean allConnectionValues = !baseUrl.isEmpty() && !clientId.isEmpty() && !clientSecret.isEmpty();
        if (anyConnectionValue && !allConnectionValues) {
            throw new IllegalArgumentException(
                    "elevate.base-url, elevate.client-id, and elevate.client-secret must either all be configured or all be blank");
        }
        if (allConnectionValues) {
            URI baseUri = validateHttpUrl("elevate.base-url", baseUrl);
            if (baseUri.getQuery() != null || baseUri.getFragment() != null) {
                throw new IllegalArgumentException("elevate.base-url must not contain a query or fragment");
            }
            requireSecureConnection(baseUri);
        }
        validateHttpUrl("elevate.support-bot-url", supportBotUrl);
        requirePositive("elevate.status-interval", statusInterval);
        requirePositive("elevate.sync-interval", syncInterval);
        requireNotBlank("elevate.agent-name", agentName);
        requireNotBlank("elevate.version", version);
        requireMaximumLength("elevate.agent-name", agentName, 120);
        requireMaximumLength("elevate.support-bot-url", supportBotUrl, 500);
        requireMaximumLength("elevate.version", version, 80);
    }

    public boolean configured() {
        return !baseUrl.isEmpty();
    }

    @Override
    public String toString() {
        return "ElevateProps[baseUrl=" + baseUrl + ", clientId=<redacted>, clientSecret=<redacted>, statusInterval="
                + statusInterval + ", syncInterval=" + syncInterval + ", agentName=" + agentName
                + ", supportBotUrl=" + supportBotUrl + ", version=" + version + "]";
    }

    private static String normalizeBaseUrl(String value) {
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static URI validateHttpUrl(String propertyName, String value) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (!uri.isAbsolute()
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || scheme == null
                    || !(scheme.toLowerCase(Locale.ROOT).equals("http")
                            || scheme.toLowerCase(Locale.ROOT).equals("https"))) {
                throw new IllegalArgumentException(propertyName + " must be an absolute HTTP(S) URL");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(propertyName + " must be an absolute HTTP(S) URL", e);
        }
    }

    private static void requireSecureConnection(URI uri) {
        if (uri.getScheme().equalsIgnoreCase("https") || isLoopbackHost(uri.getHost())) {
            return;
        }
        throw new IllegalArgumentException("elevate.base-url must use HTTPS unless the host is loopback");
    }

    private static boolean isLoopbackHost(@Nullable String host) {
        if (host == null) {
            return false;
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (normalizedHost.startsWith("[") && normalizedHost.endsWith("]")) {
            normalizedHost = normalizedHost.substring(1, normalizedHost.length() - 1);
        }
        return normalizedHost.equals("localhost")
                || normalizedHost.endsWith(".localhost")
                || isIpv4Loopback(normalizedHost)
                || normalizedHost.equals("::1")
                || normalizedHost.equals("0:0:0:0:0:0:0:1");
    }

    private static boolean isIpv4Loopback(String host) {
        String[] octets = host.split("\\.", -1);
        if (octets.length != 4 || !octets[0].equals("127")) {
            return false;
        }
        for (String octet : octets) {
            try {
                int value = Integer.parseInt(octet);
                if (value < 0 || value > 255) {
                    return false;
                }
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return true;
    }

    private static void requirePositive(String propertyName, Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(propertyName + " must be positive");
        }
    }

    private static void requireNotBlank(String propertyName, String value) {
        if (value.isEmpty()) {
            throw new IllegalArgumentException(propertyName + " must not be blank");
        }
    }

    private static void requireMaximumLength(String propertyName, String value, int maximumLength) {
        if (value.length() > maximumLength) {
            throw new IllegalArgumentException(propertyName + " must be at most " + maximumLength + " characters");
        }
    }
}
