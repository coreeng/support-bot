package com.coreeng.supportbot.docanalysis;

import java.time.Instant;

/** @param version as {@link DocumentationSession#version()} would report it */
public record SourceStatus(String version, Instant refreshedAt) {

    public SourceStatus {
        if (version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
    }
}
