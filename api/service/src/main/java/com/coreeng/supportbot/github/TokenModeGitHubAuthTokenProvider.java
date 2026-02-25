package com.coreeng.supportbot.github;

public final class TokenModeGitHubAuthTokenProvider implements GitHubAuthTokenProvider {
    private final String token;

    public TokenModeGitHubAuthTokenProvider(String token) {
        this.token = token;
    }

    @Override
    public String getToken() {
        return token;
    }
}
