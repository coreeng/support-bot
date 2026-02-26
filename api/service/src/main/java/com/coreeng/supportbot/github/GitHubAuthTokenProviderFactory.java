package com.coreeng.supportbot.github;

import com.coreeng.supportbot.config.PrTrackingAuthMode;
import com.coreeng.supportbot.config.PrTrackingGitHubProps;
import java.time.Clock;
import org.jspecify.annotations.Nullable;

public final class GitHubAuthTokenProviderFactory {
    private GitHubAuthTokenProviderFactory() {}

    /**
     * @param appTokenClient required when {@code githubConfig.authMode() == APP}, may be null for
     *     token mode
     */
    public static GitHubAuthTokenProvider create(
            PrTrackingGitHubProps githubConfig,
            @Nullable GitHubAppInstallationTokenClient appTokenClient,
            Clock clock) {
        if (githubConfig.authMode() == PrTrackingAuthMode.APP) {
            if (appTokenClient == null) {
                throw new IllegalStateException(
                        "GitHubAppInstallationTokenClient is required when auth-mode=app");
            }
            return new AppModeGitHubAuthTokenProvider(
                    githubConfig.apiBaseUrl(),
                    githubConfig.appId(),
                    githubConfig.installationId(),
                    githubConfig.privateKeyPem(),
                    appTokenClient,
                    clock);
        }
        return new TokenModeGitHubAuthTokenProvider(githubConfig.token());
    }
}
