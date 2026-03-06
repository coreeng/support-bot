package com.coreeng.supportbot.github;

public interface GitHubClient {
    GitHubPullRequest getPullRequest(String repositoryName, int pullNumber);
}
