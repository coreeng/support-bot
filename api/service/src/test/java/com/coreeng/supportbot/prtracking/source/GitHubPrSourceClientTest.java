package com.coreeng.supportbot.prtracking.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.config.PrTrackingProps;
import com.coreeng.supportbot.github.CodeOwnerReviewer;
import com.coreeng.supportbot.github.GitHubClient;
import com.coreeng.supportbot.github.GitHubGraphQlClient;
import com.coreeng.supportbot.github.GitHubPullRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Focuses on how {@link GitHubPrSourceClient} maps GitHub's GraphQL {@code reviewRequests} /
 * {@code reviewDecision} onto the tri-state {@code codeOwnersApproved} gate: a successful query with no
 * pending code-owner request is a satisfied gate — whether because none was ever required or because it
 * was already resolved — but a failed query must stay unresolved.
 */
class GitHubPrSourceClientTest {

    private static final String REPO = "my-org/my-repo";
    private static final int PR = 42;
    private static final RepoCoord COORD = RepoCoord.github(REPO);

    private final GitHubClient gitHubClient = mock(GitHubClient.class);
    private final GitHubGraphQlClient graphQlClient = mock(GitHubGraphQlClient.class);

    @Test
    void mapsApprovedReviewDecisionToGateSatisfied() {
        GitHubPrSourceClient client = codeownerClient();
        stubOpenPr();
        stubReview(GitHubPullRequest.ReviewDecision.APPROVED, List.of());

        assertThat(client.fetchPullRequest(COORD, PR).codeOwnersApproved()).isTrue();
    }

    @Test
    void treatsNoRequiredReviewAsGateSatisfied() {
        // reviewDecision == null from a *successful* query = GitHub requires no review at all for this PR
        // (no min-approval-count rule, no owned paths touched). The gate doesn't apply, so it's satisfied
        // and the PR can advance to the merge phase instead of stalling in OPEN forever.
        GitHubPrSourceClient client = codeownerClient();
        stubOpenPr();
        stubReview(null, List.of());

        assertThat(client.fetchPullRequest(COORD, PR).codeOwnersApproved()).isTrue();
    }

    @Test
    void treatsReviewRequiredWithNoOwnedPathsAsGateSatisfied() {
        // A repo can combine "require code-owner review" with a separate minimum-approval-count rule.
        // reviewDecision is GitHub's aggregate across BOTH, so it reports REVIEW_REQUIRED even for a PR that
        // touches zero owned paths, purely because the generic approval count isn't met yet. reviewRequests
        // is the more precise signal here: it's empty because no code-owner review was ever requested for
        // this diff, so the code-owner gate itself is satisfied (real-world repro: a PR touching only a path
        // CODEOWNERS explicitly excludes still showed reviewDecision REVIEW_REQUIRED).
        GitHubPrSourceClient client = codeownerClient();
        stubOpenPr();
        stubReview(GitHubPullRequest.ReviewDecision.REVIEW_REQUIRED, List.of());

        assertThat(client.fetchPullRequest(COORD, PR).codeOwnersApproved()).isTrue();
    }

    @Test
    void mapsPendingCodeOwnerRequestToNotSatisfied() {
        GitHubPrSourceClient client = codeownerClient();
        stubOpenPr();
        stubReview(
                GitHubPullRequest.ReviewDecision.REVIEW_REQUIRED,
                List.of(new CodeOwnerReviewer(false, "some-code-owner", null)));

        assertThat(client.fetchPullRequest(COORD, PR).codeOwnersApproved()).isFalse();
    }

    @Test
    void mapsChangesRequestedToNotSatisfied() {
        GitHubPrSourceClient client = codeownerClient();
        stubOpenPr();
        stubReview(GitHubPullRequest.ReviewDecision.CHANGES_REQUESTED, List.of());

        assertThat(client.fetchPullRequest(COORD, PR).codeOwnersApproved()).isFalse();
    }

    @Test
    void leavesGateUnresolvedWhenGraphQlQueryFails() {
        // A failed GraphQL query (fetchCodeownerReview -> null) must NOT be read as "no review required":
        // codeOwnersApproved stays null so the lifecycle keeps chasing the code owner and retries, rather
        // than advancing the PR to merge on a transient error.
        GitHubPrSourceClient client = codeownerClient();
        stubOpenPr();
        when(graphQlClient.fetchCodeownerReview(REPO, PR)).thenReturn(null);

        assertThat(client.fetchPullRequest(COORD, PR).codeOwnersApproved()).isNull();
    }

    @Test
    void doesNotQueryGraphQlForNonCodeownerRepo() {
        GitHubPrSourceClient client = new GitHubPrSourceClient(gitHubClient, graphQlClient, props(false));
        stubOpenPr();

        assertThat(client.fetchPullRequest(COORD, PR).codeOwnersApproved()).isNull();
        verifyNoInteractions(graphQlClient);
    }

    // ── helpers ──

    private GitHubPrSourceClient codeownerClient() {
        return new GitHubPrSourceClient(gitHubClient, graphQlClient, props(true));
    }

    private void stubOpenPr() {
        when(gitHubClient.getPullRequest(eq(REPO), eq(PR), anyBoolean()))
                .thenReturn(new GitHubPullRequest(
                        REPO,
                        PR,
                        Instant.now(),
                        GitHubPullRequest.PrState.OPEN,
                        true,
                        "clean",
                        List.of(),
                        List.of(),
                        "author"));
    }

    // A null decision models GitHub's "no review required at all" response; NullAway can't see the
    // record component's type-use @Nullable on CodeownerReview's constructor across compilation units.
    @SuppressWarnings("NullAway")
    private void stubReview(
            GitHubPullRequest.@Nullable ReviewDecision decision, List<CodeOwnerReviewer> pendingCodeOwners) {
        when(graphQlClient.fetchCodeownerReview(REPO, PR))
                .thenReturn(new GitHubGraphQlClient.CodeownerReview(decision, pendingCodeOwners));
    }

    private static PrTrackingProps props(boolean requiresCodeowners) {
        PrTrackingProps.Repository repo = new PrTrackingProps.Repository(
                REPO,
                "my-team",
                Provider.GITHUB,
                null,
                null,
                List.of(),
                new PrTrackingProps.Sla(null, Duration.ofHours(24), null),
                null,
                null,
                List.of(),
                requiresCodeowners,
                false);
        // enabled=false: this test exercises only findRepository / the source-client mapping, so skipping
        // the full config validation keeps the fixture minimal.
        return new PrTrackingProps(
                false, "0 * * * * *", "pr", List.of("support"), "team", "days", List.of(repo), null, null, null);
    }
}
