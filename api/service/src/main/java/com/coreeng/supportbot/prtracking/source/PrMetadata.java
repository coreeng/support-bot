package com.coreeng.supportbot.prtracking.source;

import static java.util.Objects.requireNonNull;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record PrMetadata(
        RepoCoord coord,
        int number,
        Instant createdAt,
        PrState state,
        @Nullable Boolean mergeable,
        List<String> requestedTeamReviewerLogins,
        List<Review> reviews) {
    public PrMetadata {
        requireNonNull(coord, "coord must not be null");
        requireNonNull(createdAt, "createdAt must not be null");
        requireNonNull(state, "state must not be null");
        requestedTeamReviewerLogins = List.copyOf(requestedTeamReviewerLogins);
        reviews = List.copyOf(reviews);
        if (number <= 0) {
            throw new IllegalArgumentException("number must be positive, was " + number);
        }
    }

    public boolean isOpen() {
        return state == PrState.OPEN;
    }

    public boolean isClosed() {
        return state == PrState.CLOSED || state == PrState.MERGED;
    }

    /** Returns true only when the provider has computed mergeability and confirmed the PR is mergeable. */
    public boolean isMergeable() {
        return Boolean.TRUE.equals(mergeable);
    }

    public enum PrState {
        OPEN,
        CLOSED,
        MERGED
    }
}
