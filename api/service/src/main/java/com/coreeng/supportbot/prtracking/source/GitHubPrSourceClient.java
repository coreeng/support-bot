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
            // and it's bypassed when an explicit github-team-slug is set or the repo is requires-codeowners
            // (gated on the GraphQL reviewDecision). Skipping resolution there avoids an org Members:Read call —
            // and the scope it requires — that would otherwise be dead work.
            boolean includeRequestedTeamMembers =
                    repoConfig == null || (repoConfig.githubTeamSlug() == null && !requiresCodeowners);
            GitHubPullRequest pr = gitHubClient.getPullRequest(coord.name(), prNumber, includeRequestedTeamMembers);
            // Code-owner repos: enrich with GitHub's GraphQL-only reviewDecision + asCodeOwner reviewers.
            // Only for open PRs (closed/merged need no chase), and only when configured, to avoid the
            // extra GraphQL call on every other repo. Degrades gracefully: null review leaves the signals
            // unset, so the lifecycle treats it as "code owners not yet satisfied".
            if (graphQlClient != null && pr.isOpen() && requiresCodeowners) {
                GitHubGraphQlClient.CodeownerReview review = graphQlClient.fetchCodeownerReview(coord.name(), prNumber);
                if (review != null) {
                    pr = pr.withCodeownerReview(review.reviewDecision(), review.codeOwnerReviewers());
                }
            }
            Boolean codeOwnersApproved = pr.reviewDecision() == null
                    ? null
                    : pr.reviewDecision() == GitHubPullRequest.ReviewDecision.APPROVED;
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
