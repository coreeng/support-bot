package com.coreeng.supportbot.github;

import com.coreeng.supportbot.config.PrTrackingAuthMode;
import com.coreeng.supportbot.config.PrTrackingGitHubProps;
import java.time.Clock;

public final class GitHubAuthTokenProviderFactory {
    private GitHubAuthTokenProviderFactory() {}

    public static GitHubAuthTokenProvider create(
            PrTrackingGitHubProps githubConfig, GitHubAppInstallationTokenClient appTokenClient, Clock clock) {
        if (githubConfig.authMode() == PrTrackingAuthMode.APP) {
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
