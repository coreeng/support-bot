package com.coreeng.supportbot.github;

public interface GitHubAppInstallationTokenClient {
    GitHubInstallationToken fetchInstallationToken(
            String apiBaseUrl, String appId, String installationId, String privateKeyPem);
}
