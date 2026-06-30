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
        List<Review> reviews,
        @Nullable String authorLogin,
        @Nullable Boolean codeOwnersApproved,
        List<CodeOwnerRef> codeOwnerReviewers) {
    public PrMetadata {
        requireNonNull(coord, "coord must not be null");
        requireNonNull(createdAt, "createdAt must not be null");
        requireNonNull(state, "state must not be null");
        requestedTeamReviewerLogins = List.copyOf(requestedTeamReviewerLogins);
        reviews = List.copyOf(reviews);
        codeOwnerReviewers = List.copyOf(codeOwnerReviewers);
        if (number <= 0) {
            throw new IllegalArgumentException("number must be positive, was " + number);
        }
    }

    /**
     * Convenience constructor for callers (and tests) that don't supply an author. The author is
     * captured natively by the source clients; an unknown author is represented as {@code null}.
     */
    public PrMetadata(
            RepoCoord coord,
            int number,
            Instant createdAt,
            PrState state,
            @Nullable Boolean mergeable,
            List<String> requestedTeamReviewerLogins,
            List<Review> reviews) {
        this(coord, number, createdAt, state, mergeable, requestedTeamReviewerLogins, reviews, null, null, List.of());
    }

    /**
     * Convenience constructor for callers that supply an author but no code-owner signals. The
     * code-owner gate ({@code codeOwnersApproved}) and chase list ({@code codeOwnerReviewers}) are
     * populated only for {@code requires-codeowners} repos; {@code null}/empty means "not applicable".
     */
    public PrMetadata(
            RepoCoord coord,
            int number,
            Instant createdAt,
            PrState state,
            @Nullable Boolean mergeable,
            List<String> requestedTeamReviewerLogins,
            List<Review> reviews,
            @Nullable String authorLogin) {
        this(
                coord,
                number,
                createdAt,
                state,
                mergeable,
                requestedTeamReviewerLogins,
                reviews,
                authorLogin,
                null,
                List.of());
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
