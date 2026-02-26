package com.coreeng.supportbot.github;

import static com.google.common.base.Preconditions.checkNotNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

public final class RestClientGitHubClient implements GitHubClient {
    private static final String ACCEPT_HEADER = "application/vnd.github+json";

    private final RestClient restClient;
    private final GitHubAuthTokenProvider tokenProvider;

    public RestClientGitHubClient(RestClient restClient, GitHubAuthTokenProvider tokenProvider) {
        this.restClient = restClient;
        this.tokenProvider = tokenProvider;
    }

    @Override
    public GitHubPullRequest getPullRequest(String repositoryName, int pullNumber) {
        String[] repoParts = parseRepositoryName(repositoryName);
        GitHubPullResponse response = checkNotNull(
                restClient
                        .get()
                        .uri("/repos/{owner}/{repo}/pulls/{pullNumber}", repoParts[0], repoParts[1], pullNumber)
                        .header("Authorization", "Bearer " + tokenProvider.getToken())
                        .header("Accept", ACCEPT_HEADER)
                        .retrieve()
                        .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                            throw new GitHubApiException(
                                    res.getStatusCode().value(),
                                    "GitHub API %d for PR %s#%d".formatted(
                                            res.getStatusCode().value(), repositoryName, pullNumber));
                        })
                        .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                            throw new GitHubApiException(
                                    res.getStatusCode().value(),
                                    "GitHub API %d for PR %s#%d".formatted(
                                            res.getStatusCode().value(), repositoryName, pullNumber));
                        })
                        .body(GitHubPullResponse.class),
                "GitHub API returned empty body for PR %s#%s",
                repositoryName,
                pullNumber);

        return new GitHubPullRequest(repositoryName, pullNumber, response.createdAt(), response.state());
    }

    private static String[] parseRepositoryName(String repositoryName) {
        String[] parts = repositoryName.split("/", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException("repositoryName must be in org/repo format");
        }
        return parts;
    }

    private record GitHubPullResponse(String state, @JsonProperty("created_at") Instant createdAt) {}
}
