package com.coreeng.supportbot.github;

import java.time.Instant;

public record GitHubPullRequest(String repositoryName, int pullRequestNumber, Instant createdAt, String state) {}
