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
}
