package com.coreeng.supportbot.prtracking;

import static java.util.Objects.requireNonNull;

import com.coreeng.supportbot.prtracking.source.Provider;

/**
 * A pull/merge request reference extracted from a Slack message, paired with the provider it
 * belongs to. Provider is required because downstream code (lookup, fetching, URL construction)
 * routes by it — defaulting it would silently mis-route GitLab MR links into the GitHub path.
 */
public record DetectedPr(Provider provider, String repositoryName, int pullNumber) {
    public DetectedPr {
        requireNonNull(provider, "provider must not be null");
        requireNonNull(repositoryName, "repositoryName must not be null");
        if (pullNumber <= 0) {
            throw new IllegalArgumentException("pullNumber must be greater than 0");
        }
    }
}
