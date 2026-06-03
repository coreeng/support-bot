package com.coreeng.supportbot.prtracking.source;

import static java.util.Objects.requireNonNull;

import java.time.Instant;

public record Review(String userLogin, ReviewState state, Instant submittedAt) {
    public Review {
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
