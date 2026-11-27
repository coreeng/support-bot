package com.coreeng.supportbot.github;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.jspecify.annotations.Nullable;

public interface GitHubClient {
    /**
     * Fetches PR metadata.
     *
     * @param includeRequestedTeamMembers when {@code false}, skips expanding the PR's requested team
     *     reviewers into member logins (an org Members:Read call) — leaving {@code
     *     requestedTeamReviewerLogins} empty. Callers pass {@code false} when that list won't be used, so
     *     repos that don't need it never make the call or require the scope.
     */
    GitHubPullRequest getPullRequest(String repositoryName, int pullNumber, boolean includeRequestedTeamMembers);

    /** Fetches PR metadata including requested-team-member resolution. */
    default GitHubPullRequest getPullRequest(String repositoryName, int pullNumber) {
        return getPullRequest(repositoryName, pullNumber, true);
    }

    /**
     * @return the file content, or {@code null} if the file does not exist (404)
     * @throws GitHubApiException for all other failures (auth, server errors, network)
     */
    @Nullable String getFileContent(String repositoryName, String path);

    List<String> listPullRequestFiles(String repositoryName, int pullNumber);

    /**
     * Resolves the members of a GitHub team for review validation.
     *
     * @throws GitHubApiException on any GitHub API failure (not found, auth, server errors, network)
     */
    List<String> resolveTeamReviewers(String org, String teamSlug);

    /** @throws GitHubApiException on any GitHub API failure, a missing branch included */
    String getBranchHeadCommit(String repositoryName, String branch);

    /**
     * Streams the repository's gzipped tar archive at {@code commit} to {@code reader}, and returns
     * what it returns. The stream is closed afterwards.
     *
     * @throws GitHubApiException when GitHub refuses the download or the connection fails
     * @throws java.io.UncheckedIOException wrapping an {@link IOException} thrown by the reader
     */
    <T> T downloadArchive(String repositoryName, String commit, ArchiveReader<T> reader);

    @FunctionalInterface
    interface ArchiveReader<T> {
        T read(InputStream gzippedTar) throws IOException;
    }
}
