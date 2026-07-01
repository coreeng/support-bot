package com.coreeng.supportbot.prtracking.source;

import static java.util.Objects.requireNonNull;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * {@code requestedTeamReviewerLogins} is {@code null} when the source client attempted to resolve at
 * least one of the PR's requested teams' membership and couldn't (e.g. a GitHub team-membership read
 * failed) — the requested-team review fallback is unresolved for this poll, as distinct from an empty
 * list (no teams requested / resolution not attempted). {@code TeamReviewFilter} treats {@code null} the
 * same as an explicit team lookup failure: fall back to accepting all reviews rather than trusting a
 * partial membership set.
 */
public record PrMetadata(
        RepoCoord coord,
        int number,
        Instant createdAt,
        PrState state,
        @Nullable Boolean mergeable,
        @Nullable List<String> requestedTeamReviewerLogins,
        List<Review> reviews,
        @Nullable String authorLogin,
        @Nullable Boolean codeOwnersApproved,
        List<CodeOwnerRef> codeOwnerReviewers) {
    public PrMetadata {
        requireNonNull(coord, "coord must not be null");
        requireNonNull(createdAt, "createdAt must not be null");
        requireNonNull(state, "state must not be null");
        requestedTeamReviewerLogins =
                requestedTeamReviewerLogins == null ? null : List.copyOf(requestedTeamReviewerLogins);
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
            @Nullable List<String> requestedTeamReviewerLogins,
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
            @Nullable List<String> requestedTeamReviewerLogins,
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
