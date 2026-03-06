package com.coreeng.supportbot.github;

import static java.util.Objects.requireNonNull;

import java.time.Instant;

public record GitHubPullRequest(String repositoryName, int pullRequestNumber, Instant createdAt, PrState state) {
    public GitHubPullRequest {
        requireNonNull(repositoryName, "repositoryName must not be null");
        requireNonNull(createdAt, "createdAt must not be null");
        requireNonNull(state, "state must not be null");
    }

    public boolean isOpen() {
        return state == PrState.OPEN;
    }

    public boolean isClosed() {
        return state == PrState.CLOSED || state == PrState.MERGED;
    }

    public enum PrState {
        OPEN,
        CLOSED,
        MERGED
    }
}
