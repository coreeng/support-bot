package com.coreeng.supportbot.prtracking.source;

import static java.util.Objects.requireNonNull;

public record RepoCoord(Provider provider, String name) {
    public RepoCoord {
        requireNonNull(provider, "provider must not be null");
        requireNonNull(name, "name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }

    public static RepoCoord github(String name) {
        return new RepoCoord(Provider.GITHUB, name);
    }

    public static RepoCoord gitlab(String name) {
        return new RepoCoord(Provider.GITLAB, name);
    }
}
