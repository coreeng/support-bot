package com.coreeng.supportbot.github;

import static java.util.Objects.requireNonNull;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record GitHubPullRequest(
        String repositoryName,
        int pullRequestNumber,
        Instant createdAt,
        PrState state,
        @Nullable Boolean mergeable,
        @Nullable String mergeableState) {
    public GitHubPullRequest {
        requireNonNull(repositoryName, "repositoryName must not be null");
        requireNonNull(createdAt, "createdAt must not be null");
        requireNonNull(state, "state must not be null");
        if (pullRequestNumber <= 0) {
            throw new IllegalArgumentException("pullRequestNumber must be positive, was " + pullRequestNumber);
        }
    }

    public boolean isOpen() {
        return state == PrState.OPEN;
    }

    /** Returns true only when GitHub has computed mergeability and confirmed the PR is mergeable. */
    public boolean isMergeable() {
        return Boolean.TRUE.equals(mergeable);
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
