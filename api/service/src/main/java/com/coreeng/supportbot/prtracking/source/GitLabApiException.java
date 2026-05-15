package com.coreeng.supportbot.prtracking.source;

import org.jspecify.annotations.Nullable;

/**
 * Sibling of {@code GitHubApiException} for the GitLab adapter. Carries the HTTP status code so
 * callers can distinguish 401/404 from generic failures. A status of {@code 0} means the failure
 * happened before a response arrived (network, timeout, unexpected null body).
 *
 * <p>Extends {@link PrSourceException} so the detection / team-review code paths that catch
 * provider failures uniformly continue to work for GitLab.
 */
public class GitLabApiException extends PrSourceException {
    private final int statusCode;

    public GitLabApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public GitLabApiException(int statusCode, String message, @Nullable Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
