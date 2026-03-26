package com.coreeng.supportbot.github;

import java.io.IOException;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestFileDetail;
import org.kohsuke.github.GHPullRequestReview;
import org.kohsuke.github.GHPullRequestReviewState;
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
            String decoded = repo.getFileContent(path).getContent();
            if (decoded == null) {
                throw new GitHubApiException(
                        0, "GitHub returned null content for %s in %s".formatted(path, repositoryName));
            }
            return decoded;
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
            return pr.listFiles().toList().stream()
                    .map(GHPullRequestFileDetail::getFilename)
                    .toList();
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
    public List<GitHubPullRequestReview> listReviews(String repositoryName, int pullNumber) {
        try {
            GHPullRequest pr = github.getRepository(repositoryName).getPullRequest(pullNumber);
            return pr.listReviews().toList().stream()
                    .map(review -> mapReview(review, repositoryName, pullNumber))
                    .toList();
        } catch (GHFileNotFoundException e) {
            throw new GitHubApiException(404, "PR not found: %s#%d".formatted(repositoryName, pullNumber), e);
        } catch (HttpException e) {
            throw new GitHubApiException(
                    e.getResponseCode(),
                    "GitHub API %d listing reviews for %s#%d"
                            .formatted(e.getResponseCode(), repositoryName, pullNumber),
                    e);
        } catch (IOException e) {
            throw new GitHubApiException(
                    0, "GitHub API call failed listing reviews for %s#%d".formatted(repositoryName, pullNumber), e);
        }
    }

    private static GitHubPullRequestReview mapReview(
            GHPullRequestReview review, String repositoryName, int pullNumber) {
        try {
            var user = review.getUser();
            if (user == null) {
                throw new GitHubApiException(
                        0, "GitHub returned null user for review on %s#%d".formatted(repositoryName, pullNumber));
            }
            var state = review.getState();
            if (state == null) {
                throw new GitHubApiException(
                        0, "GitHub returned null state for review on %s#%d".formatted(repositoryName, pullNumber));
            }
            var submittedAt = review.getSubmittedAt();
            if (submittedAt == null) {
                throw new GitHubApiException(
                        0,
                        "GitHub returned null submitted_at for review on %s#%d".formatted(repositoryName, pullNumber));
            }
            return new GitHubPullRequestReview(user.getLogin(), mapReviewState(state), submittedAt.toInstant());
        } catch (IOException e) {
            throw new GitHubApiException(
                    0, "GitHub API call failed mapping review for %s#%d".formatted(repositoryName, pullNumber), e);
        }
    }

    private static GitHubPullRequestReview.ReviewState mapReviewState(GHPullRequestReviewState state) {
        return switch (state) {
            case APPROVED -> GitHubPullRequestReview.ReviewState.APPROVED;
            // REQUEST_CHANGES is a deprecated alias for CHANGES_REQUESTED in hub4j
            case CHANGES_REQUESTED, REQUEST_CHANGES -> GitHubPullRequestReview.ReviewState.CHANGES_REQUESTED;
            case COMMENTED -> GitHubPullRequestReview.ReviewState.COMMENTED;
            case DISMISSED -> GitHubPullRequestReview.ReviewState.DISMISSED;
            case PENDING -> GitHubPullRequestReview.ReviewState.PENDING;
        };
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
