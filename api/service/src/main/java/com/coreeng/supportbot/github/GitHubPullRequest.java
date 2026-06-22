package com.coreeng.supportbot.github;

import static java.util.Objects.requireNonNull;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record GitHubPullRequest(
        String repositoryName,
        int pullRequestNumber,
        Instant createdAt,
        PrState state,
        @Nullable Boolean mergeable,
        @Nullable String mergeableState,
        List<String> requestedTeamReviewerLogins,
        List<GitHubPullRequestReview> reviews,
        @Nullable String authorLogin) {
    public GitHubPullRequest {
        requireNonNull(repositoryName, "repositoryName must not be null");
        requireNonNull(createdAt, "createdAt must not be null");
        requireNonNull(state, "state must not be null");
        requestedTeamReviewerLogins = List.copyOf(requestedTeamReviewerLogins);
        reviews = List.copyOf(reviews);
        if (pullRequestNumber <= 0) {
            throw new IllegalArgumentException("pullRequestNumber must be positive, was " + pullRequestNumber);
        }
    }

    /** Convenience constructor for callers (and tests) that don't supply an author. */
    public GitHubPullRequest(
            String repositoryName,
            int pullRequestNumber,
            Instant createdAt,
            PrState state,
            @Nullable Boolean mergeable,
            @Nullable String mergeableState,
            List<String> requestedTeamReviewerLogins,
            List<GitHubPullRequestReview> reviews) {
        this(
                repositoryName,
                pullRequestNumber,
                createdAt,
                state,
                mergeable,
                mergeableState,
                requestedTeamReviewerLogins,
                reviews,
                null);
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
