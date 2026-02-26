package com.coreeng.supportbot.github;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AppModeGitHubAuthTokenProvider implements GitHubAuthTokenProvider {
    private static final Logger log = LoggerFactory.getLogger(AppModeGitHubAuthTokenProvider.class);
    private static final Duration REFRESH_BEFORE_EXPIRY = Duration.ofMinutes(5);

    private final String apiBaseUrl;
    private final String appId;
    private final String installationId;
    private final String privateKeyPem;
    private final GitHubAppInstallationTokenClient tokenClient;
    private final Clock clock;

    private @Nullable GitHubInstallationToken cachedToken;

    public AppModeGitHubAuthTokenProvider(
            String apiBaseUrl,
            String appId,
            String installationId,
            String privateKeyPem,
            GitHubAppInstallationTokenClient tokenClient,
            Clock clock) {
        this.apiBaseUrl = apiBaseUrl;
        this.appId = appId;
        this.installationId = installationId;
        this.privateKeyPem = privateKeyPem;
        this.tokenClient = tokenClient;
        this.clock = clock;
    }

    @Override
    public synchronized String getToken() {
        Instant now = clock.instant();
        if (cachedToken == null || shouldRefresh(cachedToken, now)) {
            try {
                cachedToken = tokenClient.fetchInstallationToken(apiBaseUrl, appId, installationId, privateKeyPem);
            } catch (RuntimeException e) {
                if (cachedToken != null && !isExpired(cachedToken, now)) {
                    log.warn("GitHub token refresh failed, falling back to cached token (expires {})",
                            cachedToken.expiresAt(), e);
                    return cachedToken.token();
                }
                log.error("GitHub token unavailable and no valid cached token to fall back to", e);
                return "";
            }
        }
        return cachedToken.token();
    }

    private static boolean shouldRefresh(GitHubInstallationToken token, Instant now) {
        return !token.expiresAt().minus(REFRESH_BEFORE_EXPIRY).isAfter(now);
    }

    private static boolean isExpired(GitHubInstallationToken token, Instant now) {
        return !token.expiresAt().isAfter(now);
    }
}
