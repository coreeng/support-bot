package com.coreeng.supportbot.prtracking.source;

import com.coreeng.supportbot.github.GitHubApiException;
import com.coreeng.supportbot.github.GitHubClient;
import com.coreeng.supportbot.github.GitHubPullRequest;
import com.coreeng.supportbot.github.GitHubPullRequestReview;
import java.util.List;
import org.jspecify.annotations.Nullable;

public class GitHubPrSourceClient implements PrSourceClient {

    private final GitHubClient gitHubClient;

    public GitHubPrSourceClient(GitHubClient gitHubClient) {
        this.gitHubClient = gitHubClient;
    }

    @Override
    public Provider getProvider() {
        return Provider.GITHUB;
    }

    @Override
    public PrMetadata fetchPullRequest(RepoCoord coord, int prNumber) {
        expectGitHub(coord);
        try {
            GitHubPullRequest pr = gitHubClient.getPullRequest(coord.name(), prNumber);
            return new PrMetadata(
                    coord,
                    pr.pullRequestNumber(),
                    pr.createdAt(),
                    mapState(pr.state()),
                    pr.mergeable(),
                    pr.requestedTeamReviewerLogins(),
                    pr.reviews().stream().map(GitHubPrSourceClient::mapReview).toList());
        } catch (GitHubApiException e) {
            throw new PrSourceException(e.getMessage(), e);
        }
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
