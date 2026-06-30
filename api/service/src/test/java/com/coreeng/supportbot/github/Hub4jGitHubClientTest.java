package com.coreeng.supportbot.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
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

    private static final boolean SUBMITTED_AT_IS_INSTANT =
            returnTypeIsInstant(GHPullRequestReview.class, "getSubmittedAt");

    private static boolean returnTypeIsInstant(Class<?> clazz, String methodName) {
        try {
            Method m = clazz.getMethod(methodName);
            return Instant.class.equals(m.getReturnType());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static @Nullable Object hub4jTimestamp(@Nullable Instant value, boolean apiUsesInstant) {
        if (value == null) {
            return null;
        }
        return apiUsesInstant ? value : Date.from(value);
    }

    private final GitHub gitHub = mock(GitHub.class);
    private final Hub4jGitHubClient client = new Hub4jGitHubClient(gitHub);

    @Test
    void returnsPullRequestOnHappyPath() throws IOException {
        // given
        TestPullRequest pr = stubPullRequest("my-org/my-repo", 42, instant("2026-01-01T00:00:00Z"), GHIssueState.OPEN);
        pr.testMergeable = true;
        pr.testMergeableState = "clean";
        pr.testRequestedTeams = List.of();
        stubReviews(pr, List.of());

        // when
        GitHubPullRequest result = client.getPullRequest("my-org/my-repo", 42);

        // then
        assertThat(result.repositoryName()).isEqualTo("my-org/my-repo");
        assertThat(result.pullRequestNumber()).isEqualTo(42);
        assertThat(result.createdAt()).isEqualTo(instant("2026-01-01T00:00:00Z"));
        assertThat(result.state()).isEqualTo(GitHubPullRequest.PrState.OPEN);
        assertThat(result.mergeable()).isTrue();
        assertThat(result.mergeableState()).isEqualTo("clean");
    }

    @Test
    void capturesAuthorLoginFromPullRequest() throws IOException {
        // given
        TestPullRequest pr = stubPullRequest("my-org/my-repo", 42, instant("2026-01-01T00:00:00Z"), GHIssueState.OPEN);
        pr.testMergeable = true;
        pr.testMergeableState = "clean";
        pr.testRequestedTeams = List.of();
        pr.testAuthorLogin = "octocat";
        stubReviews(pr, List.of());

        // when
        GitHubPullRequest result = client.getPullRequest("my-org/my-repo", 42);

        // then
        assertThat(result.authorLogin()).isEqualTo("octocat");
    }

    @Test
    void returnsMergedStateWhenMergedAtIsNonNull() throws IOException {
        // given
        TestPullRequest pr =
                stubPullRequest("my-org/my-repo", 42, instant("2026-01-01T00:00:00Z"), GHIssueState.CLOSED);
        pr.testMergedAt = instant("2026-01-15T10:00:00Z");
        pr.testMergeable = true;
        pr.testMergeableState = "clean";

        // when
        GitHubPullRequest result = client.getPullRequest("my-org/my-repo", 42);

        // then
        assertThat(result.state()).isEqualTo(GitHubPullRequest.PrState.MERGED);
        assertThat(pr.requestedTeamsAccessCount).isZero();
        assertThat(pr.listReviewsAccessCount).isZero();
    }

    @Test
    void returnsPullRequestWithNullMergeableWhenNotYetComputed() throws IOException {
        // given
        TestPullRequest pr = stubPullRequest("my-org/my-repo", 42, instant("2026-01-01T00:00:00Z"), GHIssueState.OPEN);
        pr.testMergeable = null;
        pr.testMergeableState = null;
        pr.testRequestedTeams = List.of();
        stubReviews(pr, List.of());

        // when
        GitHubPullRequest result = client.getPullRequest("my-org/my-repo", 42);

        // then
        assertThat(result.mergeable()).isNull();
        assertThat(result.mergeableState()).isNull();
    }

    @Test
    void wrapsNullCreatedAtAsGitHubApiException() throws IOException {
        // given
        stubPullRequest("my-org/my-repo", 42, null, GHIssueState.OPEN);

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
        TestPullRequest pr = stubPullRequest("my-org/my-repo", 42, instant("2026-01-01T00:00:00Z"), GHIssueState.OPEN);
        pr.returnNullIssueState = true;

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
        TestPullRequest pr = stubOpenPullRequest("my-org/my-repo", 42);
        GHPullRequestReview approvedReview = review("alice", GHPullRequestReviewState.APPROVED, "2026-01-02T10:00:00Z");
        GHPullRequestReview pendingReview = review("bob", GHPullRequestReviewState.PENDING, "2026-01-02T11:00:00Z");
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
        TestPullRequest pr = stubOpenPullRequest("my-org/my-repo", 42);
        stubReviews(pr, List.of(review("alice", GHPullRequestReviewState.APPROVED, "2026-01-15T14:30:00Z")));

        // when
        GitHubPullRequest result = client.getPullRequest("my-org/my-repo", 42);

        // then
        assertThat(result.reviews()).hasSize(1);
        GitHubPullRequestReview mapped = result.reviews().get(0);
        assertThat(mapped.userLogin()).isEqualTo("alice");
        assertThat(mapped.state()).isEqualTo(GitHubPullRequestReview.ReviewState.APPROVED);
        assertThat(mapped.submittedAt()).isEqualTo(instant("2026-01-15T14:30:00Z"));
    }

    @Test
    void getPullRequestMapsDismissedReviewState() throws IOException {
        // given
        TestPullRequest pr = stubOpenPullRequest("my-org/my-repo", 42);
        stubReviews(pr, List.of(review("bob", GHPullRequestReviewState.DISMISSED, "2026-01-10T08:00:00Z")));

        // when
        GitHubPullRequest result = client.getPullRequest("my-org/my-repo", 42);

        // then
        assertThat(result.reviews()).hasSize(1);
        assertThat(result.reviews().get(0).state()).isEqualTo(GitHubPullRequestReview.ReviewState.DISMISSED);
    }

    @Test
    void getPullRequestMapsDeprecatedRequestChangesAlias() throws IOException {
        // given
        TestPullRequest pr = stubOpenPullRequest("my-org/my-repo", 42);
        @SuppressWarnings("deprecation")
        GHPullRequestReviewState requestChanges = GHPullRequestReviewState.REQUEST_CHANGES;
        stubReviews(pr, List.of(review("carol", requestChanges, "2026-01-12T09:00:00Z")));

        // when
        GitHubPullRequest result = client.getPullRequest("my-org/my-repo", 42);

        // then
        assertThat(result.reviews()).hasSize(1);
        assertThat(result.reviews().get(0).state()).isEqualTo(GitHubPullRequestReview.ReviewState.CHANGES_REQUESTED);
    }

    @Test
    void getPullRequestSkipsReviewsForClosedPr() throws IOException {
        // given
        TestPullRequest pr =
                stubPullRequest("my-org/my-repo", 42, instant("2026-01-01T00:00:00Z"), GHIssueState.CLOSED);
        pr.testMergeable = false;
        pr.testMergeableState = "dirty";

        // when
        GitHubPullRequest result = client.getPullRequest("my-org/my-repo", 42);

        // then
        assertThat(pr.requestedTeamsAccessCount).isZero();
        assertThat(pr.listReviewsAccessCount).isZero();
        assertThat(result.reviews()).isEmpty();
        assertThat(result.requestedTeamReviewerLogins()).isEmpty();
    }

    @Test
    void getPullRequestSkipsReviewsForMergedPr() throws IOException {
        // given
        TestPullRequest pr =
                stubPullRequest("my-org/my-repo", 42, instant("2026-01-01T00:00:00Z"), GHIssueState.CLOSED);
        pr.testMergedAt = instant("2026-01-15T10:00:00Z");
        pr.testMergeable = true;
        pr.testMergeableState = "clean";

        // when
        GitHubPullRequest result = client.getPullRequest("my-org/my-repo", 42);

        // then
        assertThat(pr.requestedTeamsAccessCount).isZero();
        assertThat(pr.listReviewsAccessCount).isZero();
        assertThat(result.reviews()).isEmpty();
        assertThat(result.requestedTeamReviewerLogins()).isEmpty();
    }

    @Test
    void getPullRequestThrowsOnRequestedTeamsIOException() throws IOException {
        // given
        TestPullRequest pr = stubOpenPullRequest("my-org/my-repo", 42);
        pr.testRequestedTeamsException = new IOException("teams API failed");

        // when / then
        assertThatThrownBy(() -> client.getPullRequest("my-org/my-repo", 42))
                .isInstanceOf(GitHubApiException.class)
                .hasMessageContaining("my-org/my-repo#42");
    }

    @Test
    void getPullRequestSkipsRequestedTeamWhoseMembersCannotBeListed() throws IOException {
        // given — two requested teams: one resolves, the other's member listing is forbidden (e.g. the token
        // lacks org Members:Read, or the team is secret). The failure must degrade, not abort the PR fetch.
        TestPullRequest pr = stubOpenPullRequest("my-org/my-repo", 42);

        GHUser alice = mock(GHUser.class);
        when(alice.getLogin()).thenReturn("alice");
        @SuppressWarnings("unchecked")
        PagedIterable<GHUser> readable = mock(PagedIterable.class);
        when(readable.toList()).thenReturn(List.of(alice));
        GHTeam readableTeam = mock(GHTeam.class);
        when(readableTeam.listMembers()).thenReturn(readable);

        @SuppressWarnings("unchecked")
        PagedIterable<GHUser> forbidden = mock(PagedIterable.class);
        when(forbidden.toList()).thenThrow(new IOException("403 Forbidden"));
        GHTeam forbiddenTeam = mock(GHTeam.class);
        when(forbiddenTeam.getName()).thenReturn("secret-team");
        when(forbiddenTeam.listMembers()).thenReturn(forbidden);

        pr.testRequestedTeams = List.of(readableTeam, forbiddenTeam);

        // when
        GitHubPullRequest result = client.getPullRequest("my-org/my-repo", 42);

        // then — the PR is still returned with the resolvable team's members; the forbidden team is skipped.
        assertThat(result.requestedTeamReviewerLogins()).containsExactly("alice");
    }

    @Test
    void getPullRequestThrowsOnReviewsIOException() throws IOException {
        // given
        TestPullRequest pr = stubOpenPullRequest("my-org/my-repo", 42);
        pr.testReviewsException = new IOException("Connection refused");

        // when / then
        assertThatThrownBy(() -> client.getPullRequest("my-org/my-repo", 42))
                .isInstanceOf(GitHubApiException.class)
                .hasMessageContaining("my-org/my-repo");
    }

    @Test
    void getPullRequestThrowsOnNullReviewUser() throws IOException {
        // given
        TestPullRequest pr = stubOpenPullRequest("my-org/my-repo", 42);
        GHPullRequestReview review = mock(GHPullRequestReview.class);
        when(review.getUser()).thenReturn(null);
        when(review.getState()).thenReturn(GHPullRequestReviewState.APPROVED);
        doAnswer(inv -> hub4jTimestamp(instant("2026-01-10T08:00:00Z"), SUBMITTED_AT_IS_INSTANT))
                .when(review)
                .getSubmittedAt();
        stubReviews(pr, List.of(review));

        // when / then
        assertThatThrownBy(() -> client.getPullRequest("my-org/my-repo", 42))
                .isInstanceOf(GitHubApiException.class)
                .hasMessageContaining("null user");
    }

    @Test
    void getPullRequestThrowsOnNullReviewSubmittedAt() throws IOException {
        // given
        TestPullRequest pr = stubOpenPullRequest("my-org/my-repo", 42);
        GHPullRequestReview review = review("alice", GHPullRequestReviewState.APPROVED, "2026-01-10T08:00:00Z");
        doAnswer(inv -> null).when(review).getSubmittedAt();
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
     * Minimal {@link GHPullRequest} for tests: avoids hub4j {@code refresh()}/HTTP, tracks access to teams and
     * reviews, and exposes merge/mergeable/review inputs as fields.
     */
    @SuppressWarnings({"rawtypes", "unchecked", "NullAway"
    }) // test double: hub4j API is @NonNull; we return null by design
    private static final class TestPullRequest extends GHPullRequest {
        int requestedTeamsAccessCount;
        int listReviewsAccessCount;

        GHIssueState issueState = GHIssueState.OPEN;
        boolean returnNullIssueState;

        @Nullable Instant testCreatedAt;

        @Nullable Instant testMergedAt;

        @Nullable Boolean testMergeable;

        @Nullable String testMergeableState;

        @Nullable String testAuthorLogin;

        List<GHTeam> testRequestedTeams = List.of();

        @Nullable IOException testRequestedTeamsException;

        @Nullable IOException testReviewsException;

        @Nullable PagedIterable reviewsIterable;

        @Override
        public Date getCreatedAt() throws IOException {
            return testCreatedAt == null ? null : Date.from(testCreatedAt);
        }

        @Override
        public GHIssueState getState() {
            if (returnNullIssueState) {
                return null;
            }
            return issueState;
        }

        @Override
        public Date getMergedAt() {
            return testMergedAt == null ? null : Date.from(testMergedAt);
        }

        @Override
        public Boolean getMergeable() throws IOException {
            return testMergeable;
        }

        @Override
        public String getMergeableState() throws IOException {
            return testMergeableState;
        }

        @Override
        public GHUser getUser() throws IOException {
            if (testAuthorLogin == null) {
                return null;
            }
            GHUser user = mock(GHUser.class);
            when(user.getLogin()).thenReturn(testAuthorLogin);
            return user;
        }

        @Override
        public List getRequestedTeams() throws IOException {
            requestedTeamsAccessCount++;
            if (testRequestedTeamsException != null) {
                throw testRequestedTeamsException;
            }
            return List.copyOf(testRequestedTeams);
        }

        @Override
        public PagedIterable listReviews() {
            listReviewsAccessCount++;
            if (testReviewsException != null) {
                PagedIterable it = mock(PagedIterable.class);
                try {
                    doThrow(testReviewsException).when(it).toList();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                return it;
            }
            if (reviewsIterable != null) {
                return reviewsIterable;
            }
            PagedIterable empty = mock(PagedIterable.class);
            try {
                doReturn(List.of()).when(empty).toList();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return empty;
        }
    }

    private TestPullRequest stubOpenPullRequest(String repositoryName, int pullNumber) throws IOException {
        TestPullRequest pr =
                stubPullRequest(repositoryName, pullNumber, instant("2026-01-01T00:00:00Z"), GHIssueState.OPEN);
        pr.testMergeable = true;
        pr.testMergeableState = "clean";
        pr.testRequestedTeams = List.of();
        return pr;
    }

    private TestPullRequest stubPullRequest(
            String repositoryName, int pullNumber, @Nullable Instant createdAt, GHIssueState state) throws IOException {
        GHRepository repo = mock(GHRepository.class);
        TestPullRequest pr = new TestPullRequest();
        pr.issueState = state;
        pr.testCreatedAt = createdAt;
        pr.testMergedAt = null;
        pr.returnNullIssueState = false;
        pr.testMergeable = null;
        pr.testMergeableState = null;
        pr.testRequestedTeams = List.of();
        pr.testRequestedTeamsException = null;
        pr.testReviewsException = null;
        pr.reviewsIterable = null;
        pr.requestedTeamsAccessCount = 0;
        pr.listReviewsAccessCount = 0;
        when(gitHub.getRepository(repositoryName)).thenReturn(repo);
        when(repo.getPullRequest(pullNumber)).thenReturn(pr);
        return pr;
    }

    @SuppressWarnings("rawtypes")
    private void stubReviews(TestPullRequest pr, List<GHPullRequestReview> reviews) {
        PagedIterable iterable = mock(PagedIterable.class);
        try {
            doReturn(reviews).when(iterable).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        pr.reviewsIterable = iterable;
    }

    private static GHPullRequestReview review(String login, GHPullRequestReviewState state, String submittedAtIso)
            throws IOException {
        GHPullRequestReview review = mock(GHPullRequestReview.class);
        GHUser user = mock(GHUser.class);
        when(review.getUser()).thenReturn(user);
        when(user.getLogin()).thenReturn(login);
        when(review.getState()).thenReturn(state);
        doAnswer(inv -> hub4jTimestamp(instant(submittedAtIso), SUBMITTED_AT_IS_INSTANT))
                .when(review)
                .getSubmittedAt();
        return review;
    }

    private static Instant instant(String iso) {
        return Instant.parse(iso);
    }
}
