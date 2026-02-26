package com.coreeng.supportbot.github;

import java.io.IOException;
import java.util.Locale;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.HttpException;

public final class Hub4jGitHubClient implements GitHubClient {
    private final GitHub github;

    public Hub4jGitHubClient(GitHub github) {
        this.github = github;
    }

    @Override
    public GitHubPullRequest getPullRequest(String repositoryName, int pullNumber) {
        try {
            GHPullRequest pr = github.getRepository(repositoryName).getPullRequest(pullNumber);
            var createdAt = pr.getCreatedAt();
            if (createdAt == null) {
                throw new GitHubApiException(
                        0, "GitHub API returned null created_at for %s#%d".formatted(repositoryName, pullNumber));
            }
            GHIssueState state = pr.getState();
            if (state == null) {
                throw new GitHubApiException(
                        0, "GitHub API returned null state for %s#%d".formatted(repositoryName, pullNumber));
            }
            return new GitHubPullRequest(
                    repositoryName,
                    pullNumber,
                    createdAt.toInstant(),
                    state.name().toLowerCase(Locale.ROOT));
        } catch (GHFileNotFoundException e) {
            throw new GitHubApiException(
                    404, "PR not found: %s#%d".formatted(repositoryName, pullNumber), e);
        } catch (HttpException e) {
            throw new GitHubApiException(
                    e.getResponseCode(),
                    "GitHub API %d for PR %s#%d".formatted(e.getResponseCode(), repositoryName, pullNumber),
                    e);
        } catch (IOException e) {
            throw new GitHubApiException(
                    0, "GitHub API call failed for PR %s#%d".formatted(repositoryName, pullNumber), e);
        }
    }
}
