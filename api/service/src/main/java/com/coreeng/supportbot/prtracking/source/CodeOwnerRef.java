package com.coreeng.supportbot.prtracking.source;

import static java.util.Objects.requireNonNull;

import org.jspecify.annotations.Nullable;

/**
 * A code owner still owed a review on a {@code requires-codeowners} PR/MR — an entry in the chase list
 * shown in the detected message. A {@link Kind#TEAM} ref carries the org-qualified slug (e.g.
 * {@code org/team}); a {@link Kind#USER} ref carries the login/username. {@code url} links to the
 * reviewer on the provider (host-correct, incl. self-hosted), or is {@code null} when the provider
 * supplies none — the message then falls back to plain text.
 */
public record CodeOwnerRef(
        Kind kind, String display, @Nullable String url) {
    public CodeOwnerRef {
        requireNonNull(kind, "kind must not be null");
        requireNonNull(display, "display must not be null");
    }

    public boolean isTeam() {
        return kind == Kind.TEAM;
    }

    public enum Kind {
        USER,
        TEAM
    }
}
