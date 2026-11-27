package com.coreeng.supportbot.config;

import static java.util.stream.Collectors.joining;

import java.util.Arrays;
import java.util.Locale;

/** How the GitHub documentation source finds the context for a thread. */
public enum GitHubSourceContext {
    /** Every page, for every thread. */
    WHOLE,
    /** The pages of the product areas a routing call chooses for the thread, and the root pages. */
    ROUTED;

    static final String PROPERTY = DocumentationAnalysisProps.PREFIX + ".source.github.context";

    public String configValue() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    static GitHubSourceContext parse(String value) {
        String normalized = value.replace('-', '_').toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(candidate -> candidate.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(PROPERTY + " must be one of "
                        + Arrays.stream(values())
                                .map(GitHubSourceContext::configValue)
                                .collect(joining(", "))
                        + " but was '" + value + "'"));
    }
}
