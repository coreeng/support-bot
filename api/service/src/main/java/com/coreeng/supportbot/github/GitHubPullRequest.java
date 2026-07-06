package com.coreeng.supportbot.github;

import static java.util.Objects.requireNonNull;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * {@code requestedTeamReviewerLogins} is {@code null} when at least one of the PR's requested teams'
 * membership couldn't be listed — the requested-team review fallback is unresolved for this poll, as
 * distinct from an empty list (no teams requested / resolution skipped). See {@code TeamReviewFilter},
 * which treats {@code null} the same as an explicit team-slug lookup failure: fall back to accepting all
 * reviews rather than trusting a partial membership set.
 */
public record GitHubPullRequest(
        String repositoryName,
        int pullRequestNumber,
        Instant createdAt,
        PrState state,
        @Nullable Boolean mergeable,
        @Nullable String mergeableState,
        @Nullable List<String> requestedTeamReviewerLogins,
        List<GitHubPullRequestReview> reviews,
        @Nullable String authorLogin,
        @Nullable ReviewDecision reviewDecision,
        List<CodeOwnerReviewer> codeOwnerReviewers) {
    public GitHubPullRequest {
        requireNonNull(repositoryName, "repositoryName must not be null");
        requireNonNull(createdAt, "createdAt must not be null");
        requireNonNull(state, "state must not be null");
        requestedTeamReviewerLogins =
                requestedTeamReviewerLogins == null ? null : List.copyOf(requestedTeamReviewerLogins);
        reviews = List.copyOf(reviews);
        codeOwnerReviewers = List.copyOf(codeOwnerReviewers);
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
            @Nullable List<String> requestedTeamReviewerLogins,
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
                null,
                null,
                List.of());
    }

    /**
     * Convenience constructor for the hub4j REST path, which supplies an author but no code-owner
     * signals — {@code reviewDecision} and the code-owner reviewer list are populated only by the
     * GraphQL client, for {@code requires-codeowners} repos.
     */
    public GitHubPullRequest(
            String repositoryName,
            int pullRequestNumber,
            Instant createdAt,
            PrState state,
            @Nullable Boolean mergeable,
            @Nullable String mergeableState,
            @Nullable List<String> requestedTeamReviewerLogins,
            List<GitHubPullRequestReview> reviews,
            @Nullable String authorLogin) {
        this(
                repositoryName,
                pullRequestNumber,
                createdAt,
                state,
                mergeable,
                mergeableState,
                requestedTeamReviewerLogins,
                reviews,
                authorLogin,
                null,
                List.of());
    }

    /** Returns a copy with the code-owner review signals (from the GraphQL client) populated. */
    public GitHubPullRequest withCodeownerReview(
            @Nullable ReviewDecision reviewDecision, List<CodeOwnerReviewer> codeOwnerReviewers) {
        return new GitHubPullRequest(
                repositoryName,
                pullRequestNumber,
                createdAt,
                state,
                mergeable,
                mergeableState,
                requestedTeamReviewerLogins,
                reviews,
                authorLogin,
                reviewDecision,
                codeOwnerReviewers);
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

    /**
     * GitHub's aggregate review verdict for the PR (GraphQL {@code reviewDecision}). When the repo's
     * branch protection requires code-owner review, {@code APPROVED} means every required code owner
     * has approved. {@code null} when not populated (the REST-only path) or when the branch requires
     * no review.
     */
    public enum ReviewDecision {
        APPROVED,
        CHANGES_REQUESTED,
        REVIEW_REQUIRED
    }

    public enum PrState {
        OPEN,
        CLOSED,
        MERGED
    }
}
