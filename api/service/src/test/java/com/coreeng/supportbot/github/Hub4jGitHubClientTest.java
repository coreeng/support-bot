package com.coreeng.supportbot.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.*;

class Hub4jGitHubClientTest {

    private final GitHub gitHub = mock(GitHub.class);
    private final Hub4jGitHubClient client = new Hub4jGitHubClient(gitHub);

    @Test
    void returnsPullRequestOnHappyPath() throws IOException {
        // given
        GHRepository repo = mock(GHRepository.class);
        GHPullRequest pr = spy(new GHPullRequest());
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        when(gitHub.getRepository("my-org/my-repo")).thenReturn(repo);
        when(repo.getPullRequest(42)).thenReturn(pr);
        setCreatedAtRaw(pr, "2026-01-01T00:00:00Z");
        setStateRaw(pr, "open");
        setMergeableRaw(pr, true);
        setMergeableStateRaw(pr, "clean");
        setRequestedTeamsRaw(pr);
        stubEmptyReviews(pr);

        // when
        GitHubPullRequest result = client.getPullRequest("my-org/my-repo", 42);

        // then
        assertThat(result.repositoryName()).isEqualTo("my-org/my-repo");
        assertThat(result.pullRequestNumber()).isEqualTo(42);
        assertThat(result.createdAt()).isEqualTo(createdAt);
        assertThat(result.state()).isEqualTo(GitHubPullRequest.PrState.OPEN);
        assertThat(result.mergeable()).isTrue();
        assertThat(result.mergeableState()).isEqualTo("clean");
    }

    @Test
    void returnsMergedStateWhenMergedAtIsNonNull() throws IOException {
        // given
        GHRepository repo = mock(GHRepository.class);
        GHPullRequest pr = spy(new GHPullRequest());
        when(gitHub.getRepository("my-org/my-repo")).thenReturn(repo);
        when(repo.getPullRequest(42)).thenReturn(pr);
        setCreatedAtRaw(pr, "2026-01-01T00:00:00Z");
        setStateRaw(pr, "closed");
        setMergedAtRaw(pr, "2026-01-15T10:00:00Z");
        setRequestedTeamsRaw(pr);
        stubEmptyReviews(pr);

        // when
        GitHubPullRequest result = client.getPullRequest("my-org/my-repo", 42);

        // then
        assertThat(result.state()).isEqualTo(GitHubPullRequest.PrState.MERGED);
    }

    @Test
    void returnsPullRequestWithNullMergeableWhenNotYetComputed() throws IOException {
        // given - mergeable/mergeableState not set, GitHub returns null when not yet computed
        GHRepository repo = mock(GHRepository.class);
        GHPullRequest pr = spy(new GHPullRequest());
        when(gitHub.getRepository("my-org/my-repo")).thenReturn(repo);
        when(repo.getPullRequest(42)).thenReturn(pr);
        setCreatedAtRaw(pr, "2026-01-01T00:00:00Z");
        setStateRaw(pr, "open");
        setRequestedTeamsRaw(pr);
        stubEmptyReviews(pr);

        // when
        GitHubPullRequest result = client.getPullRequest("my-org/my-repo", 42);

        // then
        assertThat(result.mergeable()).isNull();
        assertThat(result.mergeableState()).isNull();
    }

    @Test
    void wrapsNullCreatedAtAsGitHubApiException() throws IOException {
        // given
        GHRepository repo = mock(GHRepository.class);
        GHPullRequest pr = mock(GHPullRequest.class); // getCreatedAt() returns null by default
        when(gitHub.getRepository("my-org/my-repo")).thenReturn(repo);
        when(repo.getPullRequest(42)).thenReturn(pr);

        // when / then
        assertThatThrownBy(() -> client.getPullRequest("my-org/my-repo", 42))
                .isInstanceOf(GitHubApiException.class)
                .satisfies(
                        ex -> assertThat(((GitHubApiException) ex).statusCode()).isEqualTo(0))
                .hasMessageContaining("null created_at");
    }

    @Test
    void wrapsNullStateAsGitHubApiException() throws IOException {
        // given
        GHRepository repo = mock(GHRepository.class);
        GHPullRequest pr = new GHPullRequest();
        when(gitHub.getRepository("my-org/my-repo")).thenReturn(repo);
        when(repo.getPullRequest(42)).thenReturn(pr);
        setCreatedAtRaw(pr, "2026-01-01T00:00:00Z");
        setStateRaw(pr, null);

        // when / then
        assertThatThrownBy(() -> client.getPullRequest("my-org/my-repo", 42))
                .isInstanceOf(GitHubApiException.class)
                .satisfies(
                        ex -> assertThat(((GitHubApiException) ex).statusCode()).isEqualTo(0))
                .hasMessageContaining("null state");
    }

    @Test
    void wrapsNotFoundAsGitHubApiException() throws IOException {
        // given
        when(gitHub.getRepository("my-org/my-repo")).thenThrow(new GHFileNotFoundException("Not Found"));

        // when / then
        assertThatThrownBy(() -> client.getPullRequest("my-org/my-repo", 999))
                .isInstanceOf(GitHubApiException.class)
                .satisfies(
                        ex -> assertThat(((GitHubApiException) ex).statusCode()).isEqualTo(404))
                .hasMessageContaining("my-org/my-repo#999");
    }

    @Test
    void wrapsHttpErrorAsGitHubApiException() throws IOException {
        // given
        when(gitHub.getRepository("my-org/my-repo"))
                .thenThrow(new HttpException(401, "Unauthorized", (String) null, null));

        // when / then
        assertThatThrownBy(() -> client.getPullRequest("my-org/my-repo", 1))
                .isInstanceOf(GitHubApiException.class)
                .satisfies(
                        ex -> assertThat(((GitHubApiException) ex).statusCode()).isEqualTo(401))
                .hasMessageContaining("401");
    }

    @Test
    void wrapsIOExceptionAsGitHubApiException() throws IOException {
        // given
        when(gitHub.getRepository("my-org/my-repo")).thenThrow(new IOException("Connection refused"));

        // when / then
        assertThatThrownBy(() -> client.getPullRequest("my-org/my-repo", 1))
                .isInstanceOf(GitHubApiException.class)
                .satisfies(
                        ex -> assertThat(((GitHubApiException) ex).statusCode()).isEqualTo(0))
                .hasMessageContaining("my-org/my-repo#1");
    }

    @Test
    void getFileContentReturnsDecodedContent() throws IOException {
        // given
        GHRepository repo = mock(GHRepository.class);
        GHContent ghContent = mock(GHContent.class);
        when(gitHub.getRepository("my-org/my-repo")).thenReturn(repo);
        when(repo.getFileContent(".pr-sla.yaml")).thenReturn(ghContent);
        when(ghContent.getContent()).thenReturn("default: 48h");

        // when
        String result = client.getFileContent("my-org/my-repo", ".pr-sla.yaml");

        // then
        assertThat(result).isEqualTo("default: 48h");
    }

    @Test
    void getFileContentReturnsNullOn404() throws IOException {
        // given
        GHRepository repo = mock(GHRepository.class);
        when(gitHub.getRepository("my-org/my-repo")).thenReturn(repo);
        when(repo.getFileContent(".pr-sla.yaml")).thenThrow(new GHFileNotFoundException("Not Found"));

        // when
        String result = client.getFileContent("my-org/my-repo", ".pr-sla.yaml");

        // then
        assertThat(result).isNull();
    }

    @Test
    void getFileContentThrowsOnNullContent() throws IOException {
        // given
        GHRepository repo = mock(GHRepository.class);
        GHContent ghContent = mock(GHContent.class);
        when(gitHub.getRepository("my-org/my-repo")).thenReturn(repo);
        when(repo.getFileContent(".pr-sla.yaml")).thenReturn(ghContent);
        when(ghContent.getContent()).thenReturn(null);

        // when / then
        assertThatThrownBy(() -> client.getFileContent("my-org/my-repo", ".pr-sla.yaml"))
                .isInstanceOf(GitHubApiException.class)
                .hasMessageContaining("null content")
                .hasMessageContaining(".pr-sla.yaml");
    }

    @Test
    void getFileContentWrapsHttpException() throws IOException {
        // given
        GHRepository repo = mock(GHRepository.class);
        when(gitHub.getRepository("my-org/my-repo")).thenReturn(repo);
        when(repo.getFileContent(".pr-sla.yaml")).thenThrow(new HttpException(403, "Forbidden", (String) null, null));

        // when / then
        assertThatThrownBy(() -> client.getFileContent("my-org/my-repo", ".pr-sla.yaml"))
                .isInstanceOf(GitHubApiException.class)
                .satisfies(
                        ex -> assertThat(((GitHubApiException) ex).statusCode()).isEqualTo(403));
    }

    @Test
    void getFileContentWrapsIOException() throws IOException {
        // given
        GHRepository repo = mock(GHRepository.class);
        when(gitHub.getRepository("my-org/my-repo")).thenReturn(repo);
        when(repo.getFileContent(".pr-sla.yaml")).thenThrow(new IOException("Connection refused"));

        // when / then
        assertThatThrownBy(() -> client.getFileContent("my-org/my-repo", ".pr-sla.yaml"))
                .isInstanceOf(GitHubApiException.class)
                .hasMessageContaining(".pr-sla.yaml");
    }

    @Test
    void listPullRequestFilesReturnsFileNames() throws IOException {
        // given
        GHRepository repo = mock(GHRepository.class);
        GHPullRequest pr = mock(GHPullRequest.class);
        when(gitHub.getRepository("my-org/my-repo")).thenReturn(repo);
        when(repo.getPullRequest(42)).thenReturn(pr);

        GHPullRequestFileDetail file1 = mock(GHPullRequestFileDetail.class);
        GHPullRequestFileDetail file2 = mock(GHPullRequestFileDetail.class);
        when(file1.getFilename()).thenReturn("src/Main.java");
        when(file2.getFilename()).thenReturn("docs/README.md");

        @SuppressWarnings("unchecked")
        PagedIterable<GHPullRequestFileDetail> iterable = mock(PagedIterable.class);
        when(pr.listFiles()).thenReturn(iterable);
        when(iterable.toList()).thenReturn(List.of(file1, file2));

        // when
        List<String> result = client.listPullRequestFiles("my-org/my-repo", 42);

        // then
        assertThat(result).containsExactly("src/Main.java", "docs/README.md");
    }

    @Test
    void listPullRequestFilesWraps404() throws IOException {
        // given
        when(gitHub.getRepository("my-org/my-repo")).thenThrow(new GHFileNotFoundException("Not Found"));

        // when / then
        assertThatThrownBy(() -> client.listPullRequestFiles("my-org/my-repo", 42))
                .isInstanceOf(GitHubApiException.class)
                .satisfies(
                        ex -> assertThat(((GitHubApiException) ex).statusCode()).isEqualTo(404));
    }

    @Test
    void listPullRequestFilesWrapsHttpException() throws IOException {
        // given
        when(gitHub.getRepository("my-org/my-repo"))
                .thenThrow(new HttpException(500, "Internal Server Error", (String) null, null));

        // when / then
        assertThatThrownBy(() -> client.listPullRequestFiles("my-org/my-repo", 42))
                .isInstanceOf(GitHubApiException.class)
                .satisfies(
                        ex -> assertThat(((GitHubApiException) ex).statusCode()).isEqualTo(500));
    }

    @Test
    void listPullRequestFilesWrapsIOException() throws IOException {
        // given
        when(gitHub.getRepository("my-org/my-repo")).thenThrow(new IOException("Connection refused"));

        // when / then
        assertThatThrownBy(() -> client.listPullRequestFiles("my-org/my-repo", 42))
                .isInstanceOf(GitHubApiException.class)
                .hasMessageContaining("my-org/my-repo#42");
    }

    @Test
    void getPullRequestFiltersOutPendingReviews() throws IOException {
        // given
        GHRepository repo = mock(GHRepository.class);
        GHPullRequest pr = spy(new GHPullRequest());
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        when(gitHub.getRepository("my-org/my-repo")).thenReturn(repo);
        when(repo.getPullRequest(42)).thenReturn(pr);
        setCreatedAtRaw(pr, "2026-01-01T00:00:00Z");
        setStateRaw(pr, "open");
        setRequestedTeamsRaw(pr);

        GHUser approvedUser = mock(GHUser.class);
        GHPullRequestReview approvedReview =
                spyReview(approvedUser, "alice", GHPullRequestReviewState.APPROVED, "2026-01-02T10:00:00Z");
        GHUser pendingUser = mock(GHUser.class);
        GHPullRequestReview pendingReview =
                spyReview(pendingUser, "bob", GHPullRequestReviewState.PENDING, "2026-01-02T11:00:00Z");

        stubReviews(pr, List.of(approvedReview, pendingReview));

        // when
        GitHubPullRequest result = client.getPullRequest("my-org/my-repo", 42);

        // then
        assertThat(result.reviews()).hasSize(1);
        assertThat(result.reviews().get(0).userLogin()).isEqualTo("alice");
        assertThat(result.reviews().get(0).state()).isEqualTo(GitHubPullRequestReview.ReviewState.APPROVED);
    }

    @Test
    void getPullRequestMapsApprovedReviewCorrectly() throws IOException {
        // given
        GHRepository repo = mock(GHRepository.class);
        GHPullRequest pr = spy(new GHPullRequest());
        when(gitHub.getRepository("my-org/my-repo")).thenReturn(repo);
        when(repo.getPullRequest(42)).thenReturn(pr);
        setCreatedAtRaw(pr, "2026-01-01T00:00:00Z");
        setStateRaw(pr, "open");
        setRequestedTeamsRaw(pr);

        GHUser user = mock(GHUser.class);
        GHPullRequestReview review =
                spyReview(user, "alice", GHPullRequestReviewState.APPROVED, "2026-01-15T14:30:00Z");
        stubReviews(pr, List.of(review));

        // when
        GitHubPullRequest result = client.getPullRequest("my-org/my-repo", 42);

        // then
        assertThat(result.reviews()).hasSize(1);
        GitHubPullRequestReview mapped = result.reviews().get(0);
        assertThat(mapped.userLogin()).isEqualTo("alice");
        assertThat(mapped.state()).isEqualTo(GitHubPullRequestReview.ReviewState.APPROVED);
        assertThat(mapped.submittedAt()).isEqualTo(Instant.parse("2026-01-15T14:30:00Z"));
    }

    @Test
    void getPullRequestMapsDismissedReviewState() throws IOException {
        // given
        GHRepository repo = mock(GHRepository.class);
        GHPullRequest pr = spy(new GHPullRequest());
        when(gitHub.getRepository("my-org/my-repo")).thenReturn(repo);
        when(repo.getPullRequest(42)).thenReturn(pr);
        setCreatedAtRaw(pr, "2026-01-01T00:00:00Z");
        setStateRaw(pr, "open");
        setRequestedTeamsRaw(pr);

        GHUser user = mock(GHUser.class);
        GHPullRequestReview review = spyReview(user, "bob", GHPullRequestReviewState.DISMISSED, "2026-01-10T08:00:00Z");
        stubReviews(pr, List.of(review));

        // when
        GitHubPullRequest result = client.getPullRequest("my-org/my-repo", 42);

        // then
        assertThat(result.reviews()).hasSize(1);
        assertThat(result.reviews().get(0).state()).isEqualTo(GitHubPullRequestReview.ReviewState.DISMISSED);
    }

    @Test
    void getPullRequestMapsDeprecatedRequestChangesAlias() throws IOException {
        // given
        GHRepository repo = mock(GHRepository.class);
        GHPullRequest pr = spy(new GHPullRequest());
        when(gitHub.getRepository("my-org/my-repo")).thenReturn(repo);
        when(repo.getPullRequest(42)).thenReturn(pr);
        setCreatedAtRaw(pr, "2026-01-01T00:00:00Z");
        setStateRaw(pr, "open");
        setRequestedTeamsRaw(pr);

        GHUser user = mock(GHUser.class);
        @SuppressWarnings("deprecation")
        GHPullRequestReviewState requestChanges = GHPullRequestReviewState.REQUEST_CHANGES;
        GHPullRequestReview review = spyReview(user, "carol", requestChanges, "2026-01-12T09:00:00Z");
        stubReviews(pr, List.of(review));

        // when
        GitHubPullRequest result = client.getPullRequest("my-org/my-repo", 42);

        // then
        assertThat(result.reviews()).hasSize(1);
        assertThat(result.reviews().get(0).state()).isEqualTo(GitHubPullRequestReview.ReviewState.CHANGES_REQUESTED);
    }

    @Test
    void getPullRequestThrowsOnReviewsIOException() throws IOException {
        // given
        GHRepository repo = mock(GHRepository.class);
        GHPullRequest pr = spy(new GHPullRequest());
        when(gitHub.getRepository("my-org/my-repo")).thenReturn(repo);
        when(repo.getPullRequest(42)).thenReturn(pr);
        setCreatedAtRaw(pr, "2026-01-01T00:00:00Z");
        setStateRaw(pr, "open");
        setRequestedTeamsRaw(pr);

        @SuppressWarnings("unchecked")
        PagedIterable<GHPullRequestReview> iterable = mock(PagedIterable.class);
        doReturn(iterable).when(pr).listReviews();
        when(iterable.toList()).thenThrow(new IOException("Connection refused"));

        // when / then — IOException propagates as GitHubApiException so callers can skip the PR
        assertThatThrownBy(() -> client.getPullRequest("my-org/my-repo", 42))
                .isInstanceOf(GitHubApiException.class)
                .hasMessageContaining("my-org/my-repo");
    }

    @Test
    void getPullRequestThrowsOnNullReviewUser() throws IOException {
        // given
        GHRepository repo = mock(GHRepository.class);
        GHPullRequest pr = spy(new GHPullRequest());
        when(gitHub.getRepository("my-org/my-repo")).thenReturn(repo);
        when(repo.getPullRequest(42)).thenReturn(pr);
        setCreatedAtRaw(pr, "2026-01-01T00:00:00Z");
        setStateRaw(pr, "open");
        setRequestedTeamsRaw(pr);

        GHPullRequestReview review = spy(new GHPullRequestReview());
        when(review.getUser()).thenReturn(null);
        when(review.getState()).thenReturn(GHPullRequestReviewState.APPROVED);
        when(review.getSubmittedAt()).thenReturn(Date.from(Instant.parse("2026-01-10T08:00:00Z")));
        stubReviews(pr, List.of(review));

        // when / then
        assertThatThrownBy(() -> client.getPullRequest("my-org/my-repo", 42))
                .isInstanceOf(GitHubApiException.class)
                .hasMessageContaining("null user");
    }

    @Test
    void getPullRequestThrowsOnNullReviewSubmittedAt() throws IOException {
        // given
        GHRepository repo = mock(GHRepository.class);
        GHPullRequest pr = spy(new GHPullRequest());
        when(gitHub.getRepository("my-org/my-repo")).thenReturn(repo);
        when(repo.getPullRequest(42)).thenReturn(pr);
        setCreatedAtRaw(pr, "2026-01-01T00:00:00Z");
        setStateRaw(pr, "open");
        setRequestedTeamsRaw(pr);

        GHUser user = mock(GHUser.class);
        GHPullRequestReview review = spy(new GHPullRequestReview());
        when(review.getUser()).thenReturn(user);
        when(user.getLogin()).thenReturn("alice");
        when(review.getState()).thenReturn(GHPullRequestReviewState.APPROVED);
        when(review.getSubmittedAt()).thenReturn(null);
        stubReviews(pr, List.of(review));

        // when / then
        assertThatThrownBy(() -> client.getPullRequest("my-org/my-repo", 42))
                .isInstanceOf(GitHubApiException.class)
                .hasMessageContaining("null submitted_at");
    }

    @Test
    void resolveTeamReviewersReturnsLogins() throws IOException {
        // given
        GHOrganization org = mock(GHOrganization.class);
        GHTeam team = mock(GHTeam.class);
        when(gitHub.getOrganization("my-org")).thenReturn(org);
        when(org.getTeamBySlug("platform-team")).thenReturn(team);

        GHUser alice = mock(GHUser.class);
        GHUser bob = mock(GHUser.class);
        when(alice.getLogin()).thenReturn("alice");
        when(bob.getLogin()).thenReturn("bob");

        @SuppressWarnings("unchecked")
        PagedIterable<GHUser> iterable = mock(PagedIterable.class);
        when(team.listMembers()).thenReturn(iterable);
        when(iterable.toList()).thenReturn(List.of(alice, bob));

        // when
        List<String> result = client.resolveTeamReviewers("my-org", "platform-team");

        // then
        assertThat(result).containsExactly("alice", "bob");
    }

    @Test
    void resolveTeamReviewersWraps404() throws IOException {
        // given
        when(gitHub.getOrganization("my-org")).thenThrow(new GHFileNotFoundException("Not Found"));

        // when / then
        assertThatThrownBy(() -> client.resolveTeamReviewers("my-org", "platform-team"))
                .isInstanceOf(GitHubApiException.class)
                .satisfies(
                        ex -> assertThat(((GitHubApiException) ex).statusCode()).isEqualTo(404));
    }

    @Test
    void resolveTeamReviewersWrapsHttpException() throws IOException {
        // given
        when(gitHub.getOrganization("my-org")).thenThrow(new HttpException(403, "Forbidden", (String) null, null));

        // when / then
        assertThatThrownBy(() -> client.resolveTeamReviewers("my-org", "platform-team"))
                .isInstanceOf(GitHubApiException.class)
                .satisfies(
                        ex -> assertThat(((GitHubApiException) ex).statusCode()).isEqualTo(403));
    }

    @Test
    void resolveTeamReviewersWrapsIOException() throws IOException {
        // given
        when(gitHub.getOrganization("my-org")).thenThrow(new IOException("Connection refused"));

        // when / then
        assertThatThrownBy(() -> client.resolveTeamReviewers("my-org", "platform-team"))
                .isInstanceOf(GitHubApiException.class)
                .satisfies(
                        ex -> assertThat(((GitHubApiException) ex).statusCode()).isEqualTo(0))
                .hasMessageContaining("my-org/platform-team");
    }

    /**
     * Creates a spy on a real GHPullRequestReview and stubs the methods we need.
     * We use spy instead of mock because GHObject.getId() has @WithBridgeMethods
     * which generates bridge methods that are incompatible with Mockito mock stubbing.
     */
    private static GHPullRequestReview spyReview(
            GHUser user, String login, GHPullRequestReviewState state, String submittedAtIso) throws IOException {
        GHPullRequestReview review = spy(new GHPullRequestReview());
        when(review.getUser()).thenReturn(user);
        when(user.getLogin()).thenReturn(login);
        when(review.getState()).thenReturn(state);
        when(review.getSubmittedAt()).thenReturn(Date.from(Instant.parse(submittedAtIso)));
        return review;
    }

    @SuppressWarnings("unchecked")
    private static void stubEmptyReviews(GHPullRequest pr) throws IOException {
        PagedIterable<GHPullRequestReview> iterable = mock(PagedIterable.class);
        doReturn(iterable).when(pr).listReviews();
        when(iterable.toList()).thenReturn(List.of());
    }

    @SuppressWarnings("unchecked")
    private static void stubReviews(GHPullRequest pr, List<GHPullRequestReview> reviews) throws IOException {
        PagedIterable<GHPullRequestReview> iterable = mock(PagedIterable.class);
        doReturn(iterable).when(pr).listReviews();
        when(iterable.toList()).thenReturn(reviews);
    }

    private static void setCreatedAtRaw(GHPullRequest pr, String createdAtRaw) {
        setFieldOnClassHierarchy(pr, "createdAt", createdAtRaw);
    }

    private static void setStateRaw(GHPullRequest pr, @Nullable String stateRaw) {
        setFieldOnClassHierarchy(pr, "state", stateRaw);
    }

    private static void setMergedAtRaw(GHPullRequest pr, @Nullable String mergedAtRaw) {
        setFieldOnClassHierarchy(pr, "merged_at", mergedAtRaw);
    }

    private static void setMergeableRaw(GHPullRequest pr, @Nullable Boolean mergeable) {
        setFieldOnClassHierarchy(pr, "mergeable", mergeable);
    }

    private static void setMergeableStateRaw(GHPullRequest pr, @Nullable String mergeableState) {
        setFieldOnClassHierarchy(pr, "mergeable_state", mergeableState);
    }

    private static void setRequestedTeamsRaw(GHPullRequest pr) {
        setFieldOnClassHierarchy(pr, "requested_teams", new GHTeam[0]);
    }

    private static void setFieldOnClassHierarchy(Object target, String fieldName, @Nullable Object value) {
        try {
            Class<?> type = target.getClass();
            Field field = null;
            while (type != null && field == null) {
                try {
                    field = type.getDeclaredField(fieldName);
                } catch (NoSuchFieldException ignored) {
                    type = type.getSuperclass();
                }
            }
            if (field == null) {
                throw new NoSuchFieldException(fieldName);
            }
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new LinkageError("Failed to set %s on GHPullRequest test object".formatted(fieldName), e);
        }
    }
}
