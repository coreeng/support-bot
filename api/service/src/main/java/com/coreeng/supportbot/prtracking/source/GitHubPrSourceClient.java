package com.coreeng.supportbot.prtracking.source;

import com.coreeng.supportbot.config.PrTrackingProps;
import com.coreeng.supportbot.github.CodeOwnerReviewer;
import com.coreeng.supportbot.github.GitHubApiException;
import com.coreeng.supportbot.github.GitHubClient;
import com.coreeng.supportbot.github.GitHubGraphQlClient;
import com.coreeng.supportbot.github.GitHubPullRequest;
import com.coreeng.supportbot.github.GitHubPullRequestReview;
import java.util.List;
import org.jspecify.annotations.Nullable;

public class GitHubPrSourceClient implements PrSourceClient {

    private final GitHubClient gitHubClient;
    private final @Nullable GitHubGraphQlClient graphQlClient;
    private final PrTrackingProps props;

    public GitHubPrSourceClient(
            GitHubClient gitHubClient, @Nullable GitHubGraphQlClient graphQlClient, PrTrackingProps props) {
        this.gitHubClient = gitHubClient;
        this.graphQlClient = graphQlClient;
        this.props = props;
    }

    @Override
    public Provider getProvider() {
        return Provider.GITHUB;
    }

    @Override
    public PrMetadata fetchPullRequest(RepoCoord coord, int prNumber) {
        expectGitHub(coord);
        try {
            // Resolve the repo config once and derive both flags from it (one lookup, not two scans).
            PrTrackingProps.@Nullable Repository repoConfig = props.findRepository(Provider.GITHUB, coord.name());
            boolean requiresCodeowners = repoConfig != null && repoConfig.requiresCodeowners();
            // Only the requested-team review fallback in TeamReviewFilter consumes requestedTeamReviewerLogins,
            // and it's bypassed when an explicit github-team-slug is set or the repo is requires-codeowners.
            // For requires-codeowners repos the lifecycle derives its verdict from the GraphQL reviewDecision
            // aggregate, not the REST reviews this fallback would filter (see PrLifecyclePoller#observe), so
            // the fallback is dead there — skipping resolution avoids an org Members:Read call, and the scope
            // it requires, that would otherwise be dead work.
            boolean includeRequestedTeamMembers =
                    repoConfig == null || (repoConfig.githubTeamSlug() == null && !requiresCodeowners);
            GitHubPullRequest pr = gitHubClient.getPullRequest(coord.name(), prNumber, includeRequestedTeamMembers);
            // Code-owner repos: enrich with GitHub's GraphQL-only reviewDecision + asCodeOwner reviewers.
            // Only for open PRs (closed/merged need no chase), and only when configured, to avoid the
            // extra GraphQL call on every other repo.
            //
            // codeOwnersApproved is a deliberate tri-state:
            //   true  — the gate is satisfied: a *successful* GraphQL query returned either APPROVED (every
            //           required code owner approved) or no reviewDecision at all. GitHub reports no
            //           reviewDecision when the PR's changed paths require no code-owner review, so the gate
            //           doesn't apply and the PR should advance to the merge phase, not stall in OPEN.
            //   false — a required code-owner review is still outstanding (REVIEW_REQUIRED / CHANGES_REQUESTED).
            //   null  — not applicable / unknown: not a code-owner repo, a closed PR, no GraphQL client, or
            //           the query FAILED. A failed query must stay null (never read as "no review required"),
            //           so the lifecycle keeps chasing the code owner and retries next poll rather than
            //           advancing a PR to merge on a transient GraphQL error.
            //
            // Note the deliberate asymmetry with GitLabPrSourceClient, which fails *closed* on the analogous
            // "no code_owner rule" case: there an empty rule set usually means the instance lacks Code Owners
            // (CE/Free) rather than a per-PR "no owned paths", whereas GitHub's successful reviewDecision is a
            // definitive per-PR signal we can trust.
            Boolean codeOwnersApproved = null;
            // A code owner requesting changes (reviewDecision == CHANGES_REQUESTED) is a distinct signal from
            // the gate being merely unsatisfied (REVIEW_REQUIRED): the lifecycle surfaces the former to the
            // tenant as CHANGES_REQUESTED and pauses/holds accordingly, while the latter just waits. It is
            // the *aggregate* code-owner decision, so — unlike a raw REST review — a non-code-owner drive-by
            // never flips it.
            boolean codeownerChangesRequested = false;
            if (graphQlClient != null && pr.isOpen() && requiresCodeowners) {
                GitHubGraphQlClient.CodeownerReview review = graphQlClient.fetchCodeownerReview(coord.name(), prNumber);
                if (review != null) {
                    pr = pr.withCodeownerReview(review.reviewDecision(), review.codeOwnerReviewers());
                    codeOwnersApproved = review.reviewDecision() == null
                            || review.reviewDecision() == GitHubPullRequest.ReviewDecision.APPROVED;
                    codeownerChangesRequested =
                            review.reviewDecision() == GitHubPullRequest.ReviewDecision.CHANGES_REQUESTED;
                }
            }
            return new PrMetadata(
                    coord,
                    pr.pullRequestNumber(),
                    pr.createdAt(),
                    mapState(pr.state()),
                    pr.mergeable(),
                    pr.requestedTeamReviewerLogins(),
                    pr.reviews().stream().map(GitHubPrSourceClient::mapReview).toList(),
                    pr.authorLogin(),
                    codeOwnersApproved,
                    codeownerChangesRequested,
                    pr.codeOwnerReviewers().stream()
                            .map(GitHubPrSourceClient::mapCodeOwner)
                            .toList());
        } catch (GitHubApiException e) {
            throw new PrSourceException(e.getMessage(), e);
        }
    }

    private static CodeOwnerRef mapCodeOwner(CodeOwnerReviewer reviewer) {
        return new CodeOwnerRef(
                reviewer.team() ? CodeOwnerRef.Kind.TEAM : CodeOwnerRef.Kind.USER, reviewer.display(), reviewer.url());
    }

    @Override
    public @Nullable String fetchFileContents(RepoCoord coord, String path) {
        expectGitHub(coord);
        try {
            return gitHubClient.getFileContent(coord.name(), path);
        } catch (GitHubApiException e) {
            throw new PrSourceException(e.getMessage(), e);
        }
    }

    @Override
    public List<String> listChangedFiles(RepoCoord coord, int prNumber) {
        expectGitHub(coord);
        try {
            return gitHubClient.listPullRequestFiles(coord.name(), prNumber);
        } catch (GitHubApiException e) {
            throw new PrSourceException(e.getMessage(), e);
        }
    }

    @Override
    public List<String> resolveTeamMembers(RepoCoord coord, String teamRef) {
        expectGitHub(coord);
        String org = orgFromRepoName(coord.name());
        try {
            return gitHubClient.resolveTeamReviewers(org, teamRef);
        } catch (GitHubApiException e) {
            throw new PrSourceException(e.getMessage(), e);
        }
    }

    private static void expectGitHub(RepoCoord coord) {
        if (coord.provider() != Provider.GITHUB) {
            throw new IllegalArgumentException(
                    "GitHubPrSourceClient called with non-GitHub coord: " + coord.provider());
        }
    }

    private static String orgFromRepoName(String repoName) {
        int slash = repoName.indexOf('/');
        if (slash <= 0) {
            throw new IllegalArgumentException("Invalid GitHub repo name (expected org/repo): " + repoName);
        }
        return repoName.substring(0, slash);
    }

    private static PrMetadata.PrState mapState(GitHubPullRequest.PrState state) {
        return switch (state) {
            case OPEN -> PrMetadata.PrState.OPEN;
            case CLOSED -> PrMetadata.PrState.CLOSED;
            case MERGED -> PrMetadata.PrState.MERGED;
        };
    }

    private static Review mapReview(GitHubPullRequestReview review) {
        return new Review(review.userLogin(), mapReviewState(review.state()), review.submittedAt());
    }

    private static Review.ReviewState mapReviewState(GitHubPullRequestReview.ReviewState state) {
        return switch (state) {
            case APPROVED -> Review.ReviewState.APPROVED;
            case CHANGES_REQUESTED -> Review.ReviewState.CHANGES_REQUESTED;
            case COMMENTED -> Review.ReviewState.COMMENTED;
            case DISMISSED -> Review.ReviewState.DISMISSED;
            case PENDING -> Review.ReviewState.PENDING;
        };
    }
}
