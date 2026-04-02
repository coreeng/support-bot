package com.coreeng.supportbot.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class GitHubPullRequestReviewTest {

    private static final Instant SUBMITTED_AT = Instant.parse("2026-03-01T10:00:00Z");

    @SuppressWarnings("NullAway")
    @Test
    void rejectsNullUserLogin() {
        assertThatThrownBy(() ->
                        new GitHubPullRequestReview(null, GitHubPullRequestReview.ReviewState.APPROVED, SUBMITTED_AT))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("userLogin");
    }

    @Test
    void rejectsBlankUserLogin() {
        assertThatThrownBy(() ->
                        new GitHubPullRequestReview("  ", GitHubPullRequestReview.ReviewState.APPROVED, SUBMITTED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userLogin must not be blank");
    }

    @SuppressWarnings("NullAway")
    @Test
    void rejectsNullState() {
        assertThatThrownBy(() -> new GitHubPullRequestReview("user", null, SUBMITTED_AT))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("state");
    }

    @SuppressWarnings("NullAway")
    @Test
    void rejectsNullSubmittedAt() {
        assertThatThrownBy(
                        () -> new GitHubPullRequestReview("user", GitHubPullRequestReview.ReviewState.APPROVED, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("submittedAt");
    }

    @Test
    void isApprovedReturnsTrueForApproved() {
        var review = new GitHubPullRequestReview("user", GitHubPullRequestReview.ReviewState.APPROVED, SUBMITTED_AT);
        assertThat(review.isApproved()).isTrue();
        assertThat(review.requestsChanges()).isFalse();
    }

    @Test
    void requestsChangesReturnsTrueForChangesRequested() {
        var review = new GitHubPullRequestReview(
                "user", GitHubPullRequestReview.ReviewState.CHANGES_REQUESTED, SUBMITTED_AT);
        assertThat(review.requestsChanges()).isTrue();
        assertThat(review.isApproved()).isFalse();
    }

    @Test
    void convenienceMethodsReturnFalseForCommented() {
        var review = new GitHubPullRequestReview("user", GitHubPullRequestReview.ReviewState.COMMENTED, SUBMITTED_AT);
        assertThat(review.isApproved()).isFalse();
        assertThat(review.requestsChanges()).isFalse();
    }

    @Test
    void convenienceMethodsReturnFalseForDismissed() {
        var review = new GitHubPullRequestReview("user", GitHubPullRequestReview.ReviewState.DISMISSED, SUBMITTED_AT);
        assertThat(review.isApproved()).isFalse();
        assertThat(review.requestsChanges()).isFalse();
    }

    @Test
    void convenienceMethodsReturnFalseForPending() {
        var review = new GitHubPullRequestReview("user", GitHubPullRequestReview.ReviewState.PENDING, SUBMITTED_AT);
        assertThat(review.isApproved()).isFalse();
        assertThat(review.requestsChanges()).isFalse();
    }
}
