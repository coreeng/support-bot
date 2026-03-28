package com.coreeng.supportbot.github;

import java.util.List;
import org.jspecify.annotations.Nullable;

public interface GitHubClient {
    GitHubPullRequest getPullRequest(String repositoryName, int pullNumber);

    /**
     * @return the file content, or {@code null} if the file does not exist (404)
     * @throws GitHubApiException for all other failures (auth, server errors, network)
     */
    @Nullable String getFileContent(String repositoryName, String path);

    List<String> listPullRequestFiles(String repositoryName, int pullNumber);

    /**
     * @return all reviews for the given pull request
     * @throws GitHubApiException on any GitHub API failure (not found, auth, server errors, network)
     */
    List<GitHubPullRequestReview> listReviews(String repositoryName, int pullNumber);

    /**
     * Resolves the identifiers of a GitHub team's members for review validation.
     *
     * @throws GitHubApiException on any GitHub API failure (not found, auth, server errors, network)
     */
    List<String> resolveTeamReviewers(String org, String teamSlug);

    /**
     * Resolves the identifiers of members belonging to teams currently requested to review the PR.
     * Returns an empty list if no teams are requested. Note: GitHub clears a team review request
     * once any member of that team submits a review, so this may return empty even if teams were
     * originally requested.
     *
     * @throws GitHubApiException on any GitHub API failure (not found, auth, server errors, network)
     */
    List<String> resolveRequestedReviewers(String repositoryName, int pullNumber);
}
