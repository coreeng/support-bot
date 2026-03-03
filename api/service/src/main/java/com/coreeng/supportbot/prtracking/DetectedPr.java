package com.coreeng.supportbot.prtracking;

import static java.util.Objects.requireNonNull;

public record DetectedPr(String repositoryName, int pullNumber) {
    public DetectedPr {
        requireNonNull(repositoryName, "repositoryName must not be null");
        if (pullNumber <= 0) {
            throw new IllegalArgumentException("pullNumber must be greater than 0");
        }
    }
}
