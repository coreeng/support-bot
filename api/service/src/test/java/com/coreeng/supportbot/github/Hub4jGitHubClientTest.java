package com.coreeng.supportbot.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestFileDetail;
import org.kohsuke.github.GHPullRequestReview;
import org.kohsuke.github.GHPullRequestReviewState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTeam;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.HttpException;
import org.kohsuke.github.PagedIterable;

class Hub4jGitHubClientTest {

    private final GitHub gitHub = mock(GitHub.class);
    private final Hub4jGitHubClient client = new Hub4jGitHubClient(gitHub);

    @Test
    void returnsPullRequestOnHappyPath() throws IOException {
        GHPullRequest pr = stubPullRequest("my-org/my-repo", 42, instant("2026-01-01T00:00:00Z"), GHIssueState.OPEN);
        when(pr.getMergeable()).thenReturn(true);
        when(pr.getMergeableState()).thenReturn("clean");
        when(pr.getRequestedTeams()).thenReturn(List.of());
        stubReviews(pr, List.of());

        GitHubPullRequest result = client.getPullRequest("my-org/my-repo", 42);

        assertThat(result.repositoryName()).isEqualTo("my-org/my-repo");
        assertThat(result.pullRequestNumber()).isEqualTo(42);
        assertThat(result.createdAt()).isEqualTo(instant("2026-01-01T00:00:00Z"));
        assertThat(result.state()).isEqualTo(GitHubPullRequest.PrState.OPEN);
        assertThat(result.mergeable()).isTrue();
        assertThat(result.mergeableState()).isEqualTo("clean");
    }

    @Test
    void returnsMergedStateWhenMergedAtIsNonNull() throws IOException {
        GHPullRequest pr = stubPullRequest("my-org/my-repo", 42, instant("2026-01-01T00:00:00Z"), GHIssueState.CLOSED);
        when(pr.getMergedAt()).thenReturn(date("2026-01-15T10:00:00Z"));
        when(pr.getMergeable()).thenReturn(true);
        when(pr.getMergeableState()).thenReturn("clean");

        GitHubPullRequest result = client.getPullRequest("my-org/my-repo", 42);

        assertThat(result.state()).isEqualTo(GitHubPullRequest.PrState.MERGED);
        verify(pr, never()).getRequestedTeams();
        verify(pr, never()).listReviews();
    }

    @Test
    void returnsPullRequestWithNullMergeableWhenNotYetComputed() throws IOException {
        GHPullRequest pr = stubPullRequest("my-org/my-repo", 42, instant("2026-01-01T00:00:00Z"), GHIssueState.OPEN);
        when(pr.getMergeable()).thenReturn(null);
        when(pr.getMergeableState()).thenReturn(null);
        when(pr.getRequestedTeams()).thenReturn(List.of());
        stubReviews(pr, List.of());

        GitHubPullRequest result = client.getPullRequest("my-org/my-repo", 42);

        assertThat(result.mergeable()).isNull();
        assertThat(result.mergeableState()).isNull();
    }

    @Test
    void wrapsNullCreatedAtAsGitHubApiException() throws IOException {
        stubPullRequest("my-org/my-repo", 42, null, GHIssueState.OPEN);

        assertThatThrownBy(() -> client.getPullRequest("my-org/my-repo", 42))
                .isInstanceOf(GitHubApiException.class)
                .satisfies(
                        ex -> assertThat(((GitHubApiException) ex).statusCode()).isEqualTo(0))
                .hasMessageContaining("null created_at");
    }

    @Test
    void wrapsNullStateAsGitHubApiException() throws IOException {
        GHPullRequest pr = stubPullRequest("my-org/my-repo", 42, instant("2026-01-01T00:00:00Z"), GHIssueState.OPEN);
        when(pr.getState()).thenReturn(null);

        assertThatThrownBy(() -> client.getPullRequest("my-org/my-repo", 42))
                .isInstanceOf(GitHubApiException.class)
                .satisfies(
                        ex -> assertThat(((GitHubApiException) ex).statusCode()).isEqualTo(0))
                .hasMessageContaining("null state");
    }

    @Test
    void wrapsNotFoundAsGitHubApiException() throws IOException {
        when(gitHub.getRepository("my-org/my-repo")).thenThrow(new GHFileNotFoundException("Not Found"));

        assertThatThrownBy(() -> client.getPullRequest("my-org/my-repo", 999))
                .isInstanceOf(GitHubApiException.class)
                .satisfies(
                        ex -> assertThat(((GitHubApiException) ex).statusCode()).isEqualTo(404))
                .hasMessageContaining("my-org/my-repo#999");
    }

    @Test
    void wrapsHttpErrorAsGitHubApiException() throws IOException {
        when(gitHub.getRepository("my-org/my-repo"))
                .thenThrow(new HttpException(401, "Unauthorized", (String) null, null));

        assertThatThrownBy(() -> client.getPullRequest("my-org/my-repo", 1))
                .isInstanceOf(GitHubApiException.class)
                .satisfies(
                        ex -> assertThat(((GitHubApiException) ex).statusCode()).isEqualTo(401))
                .hasMessageContaining("401");
    }

    @Test
    void wrapsIOExceptionAsGitHubApiException() throws IOException {
        when(gitHub.getRepository("my-org/my-repo")).thenThrow(new IOException("Connection refused"));

        assertThatThrownBy(() -> client.getPullRequest("my-org/my-repo", 1))
                .isInstanceOf(GitHubApiException.class)
                .satisfies(
                        ex -> assertThat(((GitHubApiException) ex).statusCode()).isEqualTo(0))
                .hasMessageContaining("my-org/my-repo#1");
    }

    @Test
    void getFileContentReturnsDecodedContent() throws IOException {
        GHRepository repo = mock(GHRepository.class);
        GHContent ghContent = mock(GHContent.class);
        when(gitHub.getRepository("my-org/my-repo")).thenReturn(repo);
        when(repo.getFileContent(".pr-sla.yaml")).thenReturn(ghContent);
        when(ghContent.getContent()).thenReturn("default: 48h");

        String result = client.getFileContent("my-org/my-repo", ".pr-sla.yaml");

        assertThat(result).isEqualTo("default: 48h");
    }

    @Test
    void getFileContentReturnsNullOn404() throws IOException {
        GHRepository repo = mock(GHRepository.class);
        when(gitHub.getRepository("my-org/my-repo")).thenReturn(repo);
        when(repo.getFileContent(".pr-sla.yaml")).thenThrow(new GHFileNotFoundException("Not Found"));

        String result = client.getFileContent("my-org/my-repo", ".pr-sla.yaml");

        assertThat(result).isNull();
    }

    @Test
    void getFileContentThrowsOnNullContent() throws IOException {
        GHRepository repo = mock(GHRepository.class);
        GHContent ghContent = mock(GHContent.class);
        when(gitHub.getRepository("my-org/my-repo")).thenReturn(repo);
        when(repo.getFileContent(".pr-sla.yaml")).thenReturn(ghContent);
        when(ghContent.getContent()).thenReturn(null);

        assertThatThrownBy(() -> client.getFileContent("my-org/my-repo", ".pr-sla.yaml"))
                .isInstanceOf(GitHubApiException.class)
                .hasMessageContaining("null content")
                .hasMessageContaining(".pr-sla.yaml");
    }

    @Test
    void getFileContentWrapsHttpException() throws IOException {
        GHRepository repo = mock(GHRepository.class);
        when(gitHub.getRepository("my-org/my-repo")).thenReturn(repo);
        when(repo.getFileContent(".pr-sla.yaml")).thenThrow(new HttpException(403, "Forbidden", (String) null, null));

        assertThatThrownBy(() -> client.getFileContent("my-org/my-repo", ".pr-sla.yaml"))
                .isInstanceOf(GitHubApiException.class)
                .satisfies(
                        ex -> assertThat(((GitHubApiException) ex).statusCode()).isEqualTo(403));
    }

    @Test
    void getFileContentWrapsIOException() throws IOException {
        GHRepository repo = mock(GHRepository.class);
        when(gitHub.getRepository("my-org/my-repo")).thenReturn(repo);
        when(repo.getFileContent(".pr-sla.yaml")).thenThrow(new IOException("Connection refused"));

        assertThatThrownBy(() -> client.getFileContent("my-org/my-repo", ".pr-sla.yaml"))
                .isInstanceOf(GitHubApiException.class)
                .hasMessageContaining(".pr-sla.yaml");
    }

    @Test
    void listPullRequestFilesReturnsFileNames() throws IOException {
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

        List<String> result = client.listPullRequestFiles("my-org/my-repo", 42);

        assertThat(result).containsExactly("src/Main.java", "docs/README.md");
    }

    @Test
    void listPullRequestFilesWraps404() throws IOException {
        when(gitHub.getRepository("my-org/my-repo")).thenThrow(new GHFileNotFoundException("Not Found"));

        assertThatThrownBy(() -> client.listPullRequestFiles("my-org/my-repo", 42))
                .isInstanceOf(GitHubApiException.class)
                .satisfies(
                        ex -> assertThat(((GitHubApiException) ex).statusCode()).isEqualTo(404));
    }

    @Test
    void listPullRequestFilesWrapsHttpException() throws IOException {
        when(gitHub.getRepository("my-org/my-repo"))
                .thenThrow(new HttpException(500, "Internal Server Error", (String) null, null));

        assertThatThrownBy(() -> client.listPullRequestFiles("my-org/my-repo", 42))
                .isInstanceOf(GitHubApiException.class)
                .satisfies(
                        ex -> assertThat(((GitHubApiException) ex).statusCode()).isEqualTo(500));
    }

    @Test
    void listPullRequestFilesWrapsIOException() throws IOException {
        when(gitHub.getRepository("my-org/my-repo")).thenThrow(new IOException("Connection refused"));

        assertThatThrownBy(() -> client.listPullRequestFiles("my-org/my-repo", 42))
                .isInstanceOf(GitHubApiException.class)
                .hasMessageContaining("my-org/my-repo#42");
    }

    @Test
    void getPullRequestFiltersOutPendingReviews() throws IOException {
        GHPullRequest pr = stubOpenPullRequest("my-org/my-repo", 42);
        GHPullRequestReview approvedReview = review("alice", GHPullRequestReviewState.APPROVED, "2026-01-02T10:00:00Z");
        GHPullRequestReview pendingReview = review("bob", GHPullRequestReviewState.PENDING, "2026-01-02T11:00:00Z");
        stubReviews(pr, List.of(approvedReview, pendingReview));

        GitHubPullRequest result = client.getPullRequest("my-org/my-repo", 42);

        assertThat(result.reviews()).hasSize(1);
        assertThat(result.reviews().get(0).userLogin()).isEqualTo("alice");
        assertThat(result.reviews().get(0).state()).isEqualTo(GitHubPullRequestReview.ReviewState.APPROVED);
    }

    @Test
    void getPullRequestMapsApprovedReviewCorrectly() throws IOException {
        GHPullRequest pr = stubOpenPullRequest("my-org/my-repo", 42);
        stubReviews(pr, List.of(review("alice", GHPullRequestReviewState.APPROVED, "2026-01-15T14:30:00Z")));

        GitHubPullRequest result = client.getPullRequest("my-org/my-repo", 42);

        assertThat(result.reviews()).hasSize(1);
        GitHubPullRequestReview mapped = result.reviews().get(0);
        assertThat(mapped.userLogin()).isEqualTo("alice");
        assertThat(mapped.state()).isEqualTo(GitHubPullRequestReview.ReviewState.APPROVED);
        assertThat(mapped.submittedAt()).isEqualTo(instant("2026-01-15T14:30:00Z"));
    }

    @Test
    void getPullRequestMapsDismissedReviewState() throws IOException {
        GHPullRequest pr = stubOpenPullRequest("my-org/my-repo", 42);
        stubReviews(pr, List.of(review("bob", GHPullRequestReviewState.DISMISSED, "2026-01-10T08:00:00Z")));

        GitHubPullRequest result = client.getPullRequest("my-org/my-repo", 42);

        assertThat(result.reviews()).hasSize(1);
        assertThat(result.reviews().get(0).state()).isEqualTo(GitHubPullRequestReview.ReviewState.DISMISSED);
    }

    @Test
    void getPullRequestMapsDeprecatedRequestChangesAlias() throws IOException {
        GHPullRequest pr = stubOpenPullRequest("my-org/my-repo", 42);
        @SuppressWarnings("deprecation")
        GHPullRequestReviewState requestChanges = GHPullRequestReviewState.REQUEST_CHANGES;
        stubReviews(pr, List.of(review("carol", requestChanges, "2026-01-12T09:00:00Z")));

        GitHubPullRequest result = client.getPullRequest("my-org/my-repo", 42);

        assertThat(result.reviews()).hasSize(1);
        assertThat(result.reviews().get(0).state()).isEqualTo(GitHubPullRequestReview.ReviewState.CHANGES_REQUESTED);
    }

    @Test
    void getPullRequestSkipsReviewsForClosedPr() throws IOException {
        GHPullRequest pr = stubPullRequest("my-org/my-repo", 42, instant("2026-01-01T00:00:00Z"), GHIssueState.CLOSED);
        when(pr.getMergeable()).thenReturn(false);
        when(pr.getMergeableState()).thenReturn("dirty");

        GitHubPullRequest result = client.getPullRequest("my-org/my-repo", 42);

        verify(pr, never()).getRequestedTeams();
        verify(pr, never()).listReviews();
        assertThat(result.reviews()).isEmpty();
        assertThat(result.requestedTeamReviewerLogins()).isEmpty();
    }

    @Test
    void getPullRequestSkipsReviewsForMergedPr() throws IOException {
        GHPullRequest pr = stubPullRequest("my-org/my-repo", 42, instant("2026-01-01T00:00:00Z"), GHIssueState.CLOSED);
        when(pr.getMergedAt()).thenReturn(date("2026-01-15T10:00:00Z"));
        when(pr.getMergeable()).thenReturn(true);
        when(pr.getMergeableState()).thenReturn("clean");

        GitHubPullRequest result = client.getPullRequest("my-org/my-repo", 42);

        verify(pr, never()).getRequestedTeams();
        verify(pr, never()).listReviews();
        assertThat(result.reviews()).isEmpty();
        assertThat(result.requestedTeamReviewerLogins()).isEmpty();
    }

    @Test
    void getPullRequestThrowsOnRequestedTeamsIOException() throws IOException {
        GHPullRequest pr = stubOpenPullRequest("my-org/my-repo", 42);
        when(pr.getRequestedTeams()).thenThrow(new IOException("teams API failed"));

        assertThatThrownBy(() -> client.getPullRequest("my-org/my-repo", 42))
                .isInstanceOf(GitHubApiException.class)
                .hasMessageContaining("my-org/my-repo#42");
    }

    @Test
    void getPullRequestThrowsOnReviewsIOException() throws IOException {
        GHPullRequest pr = stubOpenPullRequest("my-org/my-repo", 42);

        @SuppressWarnings("unchecked")
        PagedIterable<GHPullRequestReview> iterable = mock(PagedIterable.class);
        when(pr.listReviews()).thenReturn(iterable);
        when(iterable.toList()).thenThrow(new IOException("Connection refused"));

        assertThatThrownBy(() -> client.getPullRequest("my-org/my-repo", 42))
                .isInstanceOf(GitHubApiException.class)
                .hasMessageContaining("my-org/my-repo");
    }

    @Test
    void getPullRequestThrowsOnNullReviewUser() throws IOException {
        GHPullRequest pr = stubOpenPullRequest("my-org/my-repo", 42);
        GHPullRequestReview review = mock(GHPullRequestReview.class);
        when(review.getUser()).thenReturn(null);
        when(review.getState()).thenReturn(GHPullRequestReviewState.APPROVED);
        doReturn(date("2026-01-10T08:00:00Z")).when(review).getSubmittedAt();
        stubReviews(pr, List.of(review));

        assertThatThrownBy(() -> client.getPullRequest("my-org/my-repo", 42))
                .isInstanceOf(GitHubApiException.class)
                .hasMessageContaining("null user");
    }

    @Test
    void getPullRequestThrowsOnNullReviewSubmittedAt() throws IOException {
        GHPullRequest pr = stubOpenPullRequest("my-org/my-repo", 42);
        GHPullRequestReview review = review("alice", GHPullRequestReviewState.APPROVED, "2026-01-10T08:00:00Z");
        doReturn(null).when(review).getSubmittedAt();
        stubReviews(pr, List.of(review));

        assertThatThrownBy(() -> client.getPullRequest("my-org/my-repo", 42))
                .isInstanceOf(GitHubApiException.class)
                .hasMessageContaining("null submitted_at");
    }

    @Test
    void resolveTeamReviewersReturnsLogins() throws IOException {
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

        List<String> result = client.resolveTeamReviewers("my-org", "platform-team");

        assertThat(result).containsExactly("alice", "bob");
    }

    @Test
    void resolveTeamReviewersWraps404() throws IOException {
        when(gitHub.getOrganization("my-org")).thenThrow(new GHFileNotFoundException("Not Found"));

        assertThatThrownBy(() -> client.resolveTeamReviewers("my-org", "platform-team"))
                .isInstanceOf(GitHubApiException.class)
                .satisfies(
                        ex -> assertThat(((GitHubApiException) ex).statusCode()).isEqualTo(404));
    }

    @Test
    void resolveTeamReviewersWrapsHttpException() throws IOException {
        when(gitHub.getOrganization("my-org")).thenThrow(new HttpException(403, "Forbidden", (String) null, null));

        assertThatThrownBy(() -> client.resolveTeamReviewers("my-org", "platform-team"))
                .isInstanceOf(GitHubApiException.class)
                .satisfies(
                        ex -> assertThat(((GitHubApiException) ex).statusCode()).isEqualTo(403));
    }

    @Test
    void resolveTeamReviewersWrapsIOException() throws IOException {
        when(gitHub.getOrganization("my-org")).thenThrow(new IOException("Connection refused"));

        assertThatThrownBy(() -> client.resolveTeamReviewers("my-org", "platform-team"))
                .isInstanceOf(GitHubApiException.class)
                .satisfies(
                        ex -> assertThat(((GitHubApiException) ex).statusCode()).isEqualTo(0))
                .hasMessageContaining("my-org/platform-team");
    }

    private GHPullRequest stubOpenPullRequest(String repositoryName, int pullNumber) throws IOException {
        GHPullRequest pr =
                stubPullRequest(repositoryName, pullNumber, instant("2026-01-01T00:00:00Z"), GHIssueState.OPEN);
        when(pr.getMergeable()).thenReturn(true);
        when(pr.getMergeableState()).thenReturn("clean");
        when(pr.getRequestedTeams()).thenReturn(List.of());
        return pr;
    }

    private GHPullRequest stubPullRequest(
            String repositoryName, int pullNumber, @Nullable Instant createdAt, GHIssueState state) throws IOException {
        GHRepository repo = mock(GHRepository.class);
        GHPullRequest pr = mock(GHPullRequest.class);
        when(gitHub.getRepository(repositoryName)).thenReturn(repo);
        when(repo.getPullRequest(pullNumber)).thenReturn(pr);
        doReturn(createdAt == null ? null : Date.from(createdAt)).when(pr).getCreatedAt();
        when(pr.getState()).thenReturn(state);
        when(pr.getMergedAt()).thenReturn(null);
        return pr;
    }

    private static GHPullRequestReview review(String login, GHPullRequestReviewState state, String submittedAtIso)
            throws IOException {
        GHPullRequestReview review = mock(GHPullRequestReview.class);
        GHUser user = mock(GHUser.class);
        when(review.getUser()).thenReturn(user);
        when(user.getLogin()).thenReturn(login);
        when(review.getState()).thenReturn(state);
        doReturn(date(submittedAtIso)).when(review).getSubmittedAt();
        return review;
    }

    @SuppressWarnings("unchecked")
    private static void stubReviews(GHPullRequest pr, List<GHPullRequestReview> reviews) throws IOException {
        PagedIterable<GHPullRequestReview> iterable = mock(PagedIterable.class);
        when(pr.listReviews()).thenReturn(iterable);
        when(iterable.toList()).thenReturn(reviews);
    }

    private static Instant instant(String iso) {
        return Instant.parse(iso);
    }

    private static Date date(String iso) {
        return Date.from(instant(iso));
    }
}
