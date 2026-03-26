package com.coreeng.supportbot.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class GitHubPullRequestTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @SuppressWarnings("NullAway")
    @Test
    void rejectsNullRepositoryName() {
        assertThatThrownBy(
                        () -> new GitHubPullRequest(null, 1, CREATED_AT, GitHubPullRequest.PrState.OPEN, true, "clean"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("repositoryName");
    }

    @SuppressWarnings("NullAway")
    @Test
    void rejectsNullCreatedAt() {
        assertThatThrownBy(
                        () -> new GitHubPullRequest("org/repo", 1, null, GitHubPullRequest.PrState.OPEN, true, "clean"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("createdAt");
    }

    @SuppressWarnings("NullAway")
    @Test
    void rejectsNullState() {
        assertThatThrownBy(() -> new GitHubPullRequest("org/repo", 1, CREATED_AT, null, true, "clean"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("state");
    }

    @Test
    void rejectsZeroPullRequestNumber() {
        assertThatThrownBy(() ->
                        new GitHubPullRequest("org/repo", 0, CREATED_AT, GitHubPullRequest.PrState.OPEN, true, "clean"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pullRequestNumber must be positive");
    }

    @Test
    void rejectsNegativePullRequestNumber() {
        assertThatThrownBy(() -> new GitHubPullRequest(
                        "org/repo", -1, CREATED_AT, GitHubPullRequest.PrState.OPEN, true, "clean"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pullRequestNumber must be positive");
    }

    @Test
    void isMergeableReturnsTrueWhenMergeableIsTrue() {
        var pr = new GitHubPullRequest("org/repo", 1, CREATED_AT, GitHubPullRequest.PrState.OPEN, true, "clean");
        assertThat(pr.isMergeable()).isTrue();
    }

    @Test
    void isMergeableReturnsFalseWhenMergeableIsFalse() {
        var pr = new GitHubPullRequest("org/repo", 1, CREATED_AT, GitHubPullRequest.PrState.OPEN, false, "dirty");
        assertThat(pr.isMergeable()).isFalse();
    }

    @Test
    void isMergeableReturnsFalseWhenMergeableIsNull() {
        var pr = new GitHubPullRequest("org/repo", 1, CREATED_AT, GitHubPullRequest.PrState.OPEN, null, null);
        assertThat(pr.isMergeable()).isFalse();
    }

    @Test
    void isOpenReturnsTrueForOpenState() {
        var pr = new GitHubPullRequest("org/repo", 1, CREATED_AT, GitHubPullRequest.PrState.OPEN, true, "clean");
        assertThat(pr.isOpen()).isTrue();
        assertThat(pr.isClosed()).isFalse();
    }

    @Test
    void isClosedReturnsTrueForClosedAndMergedStates() {
        var closed = new GitHubPullRequest("org/repo", 1, CREATED_AT, GitHubPullRequest.PrState.CLOSED, null, null);
        var merged = new GitHubPullRequest("org/repo", 1, CREATED_AT, GitHubPullRequest.PrState.MERGED, null, null);
        assertThat(closed.isClosed()).isTrue();
        assertThat(merged.isClosed()).isTrue();
        assertThat(closed.isOpen()).isFalse();
        assertThat(merged.isOpen()).isFalse();
    }
}
