package com.coreeng.supportbot.github;

import org.jspecify.annotations.Nullable;

/**
 * A still-pending code-owner reviewer as reported by GitHub's GraphQL {@code reviewRequests}
 * ({@code asCodeOwner == true}). {@code team} distinguishes a {@code Team} reviewer — where
 * {@code display} is the org-qualified {@code combinedSlug} (e.g. {@code org/team}) — from a
 * {@code User}, where {@code display} is the login. {@code url} is GitHub's link to the reviewer
 * (host-correct on GitHub Enterprise Server too), or {@code null} when absent.
 */
public record CodeOwnerReviewer(
        boolean team, String display, @Nullable String url) {}
