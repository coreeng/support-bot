package com.coreeng.supportbot.github;

import static java.util.Objects.requireNonNull;

import java.time.Instant;

public record GitHubPullRequestReview(String userLogin, ReviewState state, Instant submittedAt) {
    public GitHubPullRequestReview {
        requireNonNull(userLogin, "userLogin must not be null");
        if (userLogin.isBlank()) {
            throw new IllegalArgumentException("userLogin must not be blank");
        }
        requireNonNull(state, "state must not be null");
        requireNonNull(submittedAt, "submittedAt must not be null");
    }

    public boolean isApproved() {
        return state == ReviewState.APPROVED;
    }

    public boolean requestsChanges() {
        return state == ReviewState.CHANGES_REQUESTED;
    }

    public enum ReviewState {
        APPROVED,
        CHANGES_REQUESTED,
        COMMENTED,
        DISMISSED,
        PENDING
    }
}
