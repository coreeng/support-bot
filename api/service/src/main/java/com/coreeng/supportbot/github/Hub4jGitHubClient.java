package com.coreeng.supportbot.github;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestFileDetail;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.HttpException;

public final class Hub4jGitHubClient implements GitHubClient {
    private final GitHub github;

    public Hub4jGitHubClient(GitHub github) {
        this.github = github;
    }

    @Override
    public @Nullable String getFileContent(String repositoryName, String path) {
        try {
            GHRepository repo = github.getRepository(repositoryName);
            return repo.getFileContent(path).getContent();
        } catch (GHFileNotFoundException e) {
            return null;
        } catch (HttpException e) {
            throw new GitHubApiException(
                    e.getResponseCode(),
                    "GitHub API %d fetching %s from %s".formatted(e.getResponseCode(), path, repositoryName),
                    e);
        } catch (IOException e) {
            throw new GitHubApiException(
                    0, "GitHub API call failed fetching %s from %s".formatted(path, repositoryName), e);
        }
    }

    @Override
    public List<String> listPullRequestFiles(String repositoryName, int pullNumber) {
        try {
            GHPullRequest pr = github.getRepository(repositoryName).getPullRequest(pullNumber);
            List<String> files = new ArrayList<>();
            for (GHPullRequestFileDetail file : pr.listFiles()) {
                files.add(file.getFilename());
            }
            return files;
        } catch (GHFileNotFoundException e) {
            throw new GitHubApiException(404, "PR not found: %s#%d".formatted(repositoryName, pullNumber), e);
        } catch (HttpException e) {
            throw new GitHubApiException(
                    e.getResponseCode(),
                    "GitHub API %d listing files for %s#%d".formatted(e.getResponseCode(), repositoryName, pullNumber),
                    e);
        } catch (IOException e) {
            throw new GitHubApiException(
                    0, "GitHub API call failed listing files for %s#%d".formatted(repositoryName, pullNumber), e);
        }
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
            GHIssueState state;
            try {
                state = pr.getState();
            } catch (RuntimeException e) {
                throw new GitHubApiException(
                        0, "GitHub API returned null state for %s#%d".formatted(repositoryName, pullNumber), e);
            }
            if (state == null) {
                throw new GitHubApiException(
                        0, "GitHub API returned null state for %s#%d".formatted(repositoryName, pullNumber));
            }
            GitHubPullRequest.PrState prState;
            if (state == GHIssueState.OPEN) {
                prState = GitHubPullRequest.PrState.OPEN;
            } else if (pr.getMergedAt() != null) {
                prState = GitHubPullRequest.PrState.MERGED;
            } else {
                prState = GitHubPullRequest.PrState.CLOSED;
            }
            return new GitHubPullRequest(repositoryName, pullNumber, createdAt.toInstant(), prState);
        } catch (IllegalArgumentException e) {
            throw new GitHubApiException(
                    0, "Invalid repository name '%s': %s".formatted(repositoryName, e.getMessage()), e);
        } catch (GHFileNotFoundException e) {
            throw new GitHubApiException(404, "PR not found: %s#%d".formatted(repositoryName, pullNumber), e);
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
