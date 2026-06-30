package com.coreeng.supportbot.github;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestFileDetail;
import org.kohsuke.github.GHPullRequestReview;
import org.kohsuke.github.GHPullRequestReviewState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTeam;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.HttpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Hub4jGitHubClient implements GitHubClient {
    private static final Logger LOG = LoggerFactory.getLogger(Hub4jGitHubClient.class);
    private final GitHub github;

    /**
     * hub4j 1.3xx returns {@link Date} for several timestamps; newer releases return {@link Instant}. Normalize so we
     * work with whichever version is on the classpath (CI/Docker may resolve a different revision than local).
     */
    private static @Nullable Instant instantFromHub4j(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant i) {
            return i;
        }
        if (value instanceof Date d) {
            return d.toInstant();
        }
        throw new IllegalStateException(
                "Unexpected timestamp type from GitHub API: " + value.getClass().getName());
    }

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
            Instant submittedAt = instantFromHub4j((Object) review.getSubmittedAt());
            if (submittedAt == null) {
                throw new GitHubApiException(
                        0,
                        "GitHub returned null submitted_at for review on %s#%d".formatted(repositoryName, pullNumber));
            }
            return new GitHubPullRequestReview(user.getLogin(), mapReviewState(state), submittedAt);
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
    public List<String> resolveTeamReviewers(String org, String teamSlug) {
        try {
            return github.getOrganization(org).getTeamBySlug(teamSlug).listMembers().toList().stream()
                    .map(GHUser::getLogin)
                    .toList();
        } catch (GHFileNotFoundException e) {
            throw new GitHubApiException(404, "Team not found: %s/%s".formatted(org, teamSlug), e);
        } catch (HttpException e) {
            throw new GitHubApiException(
                    e.getResponseCode(),
                    "GitHub API %d listing team members for %s/%s".formatted(e.getResponseCode(), org, teamSlug),
                    e);
        } catch (IOException e) {
            throw new GitHubApiException(
                    0, "GitHub API call failed listing team members for %s/%s".formatted(org, teamSlug), e);
        }
    }

    private List<String> resolveRequestedTeamMembers(GHPullRequest pr, String repositoryName, int pullNumber) {
        List<GHTeam> requestedTeams;
        try {
            requestedTeams = pr.getRequestedTeams();
        } catch (IOException e) {
            throw new GitHubApiException(
                    0,
                    "GitHub API call failed fetching requested teams for %s#%d".formatted(repositoryName, pullNumber),
                    e);
        }
        if (requestedTeams == null || requestedTeams.isEmpty()) {
            return List.of();
        }
        return requestedTeams.stream()
                .flatMap(team -> {
                    try {
                        return team.listMembers().toList().stream();
                    } catch (IOException e) {
                        // A team-membership read can fail independently of the PR read — e.g. the token lacks
                        // org "Members: Read", or the team is secret. Degrade gracefully: skip this team's
                        // members instead of aborting the whole PR fetch, which would drop the PR from tracking
                        // entirely. For requires-codeowners repos this list is unused anyway (the gate comes from
                        // the GraphQL reviewDecision); elsewhere a member's review simply won't be credited via
                        // this path until the permission is granted.
                        LOG.atWarn()
                                .setCause(e)
                                .addArgument(team::getName)
                                .addArgument(() -> repositoryName)
                                .addArgument(() -> pullNumber)
                                .log(
                                        "Could not list members of requested team {} for {}#{}; treating as no resolved members");
                        return Stream.<GHUser>empty();
                    }
                })
                .map(GHUser::getLogin)
                .distinct()
                .toList();
    }

    @Override
    public GitHubPullRequest getPullRequest(
            String repositoryName, int pullNumber, boolean includeRequestedTeamMembers) {
        try {
            GHPullRequest pr = github.getRepository(repositoryName).getPullRequest(pullNumber);
            Instant createdAt = instantFromHub4j((Object) pr.getCreatedAt());
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
            } else if (instantFromHub4j((Object) pr.getMergedAt()) != null) {
                prState = GitHubPullRequest.PrState.MERGED;
            } else {
                prState = GitHubPullRequest.PrState.CLOSED;
            }
            Boolean mergeable = pr.getMergeable();
            String mergeableState = pr.getMergeableState();
            GHUser author = pr.getUser();
            String authorLogin = author != null ? author.getLogin() : null;
            List<String> requestedTeamReviewerLogins;
            List<GitHubPullRequestReview> reviews;
            if (prState == GitHubPullRequest.PrState.OPEN) {
                // Resolving requested-team members needs org Members:Read and is only consumed by the
                // requested-team review fallback. The caller skips it when that fallback won't run (an explicit
                // github-team-slug, or a requires-codeowners repo whose gate is the GraphQL reviewDecision), so
                // those repos never pay the lookup — or need the scope.
                requestedTeamReviewerLogins = includeRequestedTeamMembers
                        ? resolveRequestedTeamMembers(pr, repositoryName, pullNumber)
                        : List.of();
                reviews = pr.listReviews().toList().stream()
                        .filter(review -> review.getState() != GHPullRequestReviewState.PENDING)
                        .map(review -> mapReview(review, repositoryName, pullNumber))
                        .toList();
            } else {
                requestedTeamReviewerLogins = List.of();
                reviews = List.of();
            }
            return new GitHubPullRequest(
                    repositoryName,
                    pullNumber,
                    createdAt,
                    prState,
                    mergeable,
                    mergeableState,
                    requestedTeamReviewerLogins,
                    reviews,
                    authorLogin);
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
