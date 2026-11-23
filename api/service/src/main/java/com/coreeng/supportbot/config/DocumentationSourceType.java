package com.coreeng.supportbot.config;

import static java.util.stream.Collectors.joining;

import java.util.Arrays;
import java.util.Locale;

public enum DocumentationSourceType {
    GITHUB;

    static final String PROPERTY = "documentation-analysis.source.type";

    public String configValue() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    static DocumentationSourceType parse(String value) {
        String normalized = value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(candidate -> candidate.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(PROPERTY + " must be one of "
                        + Arrays.stream(values())
                                .map(DocumentationSourceType::configValue)
                                .collect(joining(", "))
                        + " but was '" + value + "'"));
    }
}
