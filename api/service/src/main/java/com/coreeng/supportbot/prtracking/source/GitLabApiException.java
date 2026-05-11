package com.coreeng.supportbot.prtracking.source;

import org.jspecify.annotations.Nullable;

/**
 * Sibling of {@code GitHubApiException} for the GitLab adapter. Carries the HTTP status code so
 * callers (and {@link PrSourceException} wrapping) can distinguish 401/404 from generic failures.
 * A status of {@code 0} means the failure happened before a response arrived (network, timeout,
 * unexpected null body).
 */
public class GitLabApiException extends RuntimeException {
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
