package com.coreeng.supportbot.github;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

public final class AppModeGitHubAuthTokenProvider implements GitHubAuthTokenProvider {
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
            cachedToken = tokenClient.fetchInstallationToken(apiBaseUrl, appId, installationId, privateKeyPem);
        }
        return cachedToken.token();
    }

    private static boolean shouldRefresh(GitHubInstallationToken token, Instant now) {
        Instant refreshThreshold = token.expiresAt().minus(REFRESH_BEFORE_EXPIRY);
        return !refreshThreshold.isAfter(now);
    }
}
