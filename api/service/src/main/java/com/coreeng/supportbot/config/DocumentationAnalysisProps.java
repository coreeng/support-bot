package com.coreeng.supportbot.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Settings of the documentation analysis: the analysis's own at the top, each source's under
 * {@link Source}. Nothing is validated while {@link #enabled()} is false, so a deployment with the
 * feature off is never blocked from starting by settings it does not use. The feature also needs an
 * LLM provider; see {@link ConditionalOnDocumentationSource}.
 */
@ConfigurationProperties(prefix = "documentation-analysis")
public record DocumentationAnalysisProps(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("150000") int maxInputTokens,
        @DefaultValue("500") int maxTicketsPerDay,
        @DefaultValue("24h") Duration failureRetryDelay,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("15m") Duration runRetryDelay,
        @DefaultValue Source source) {

    private static final String PREFIX = "documentation-analysis.";

    public DocumentationAnalysisProps {
        if (enabled) {
            requirePositive(maxInputTokens, "max-input-tokens");
            requirePositive(maxTicketsPerDay, "max-tickets-per-day");
            requirePositive(failureRetryDelay, "failure-retry-delay");
            requirePositive(maxAttempts, "max-attempts");
            requirePositive(runRetryDelay, "run-retry-delay");
            source.validate();
        }
    }

    public record Source(
            @DefaultValue("github") String type,
            @DefaultValue GitHub github) {

        /** The source selected by {@code type}. Fails when it names no known source. */
        public DocumentationSourceType selectedType() {
            return DocumentationSourceType.parse(type);
        }

        void validate() {
            switch (selectedType()) {
                case GITHUB -> github.validate();
            }
        }
    }

    public record GitHub(
            @DefaultValue("") String repository,
            @DefaultValue("main") String branch,
            @DefaultValue("docs") String path,
            @DefaultValue({".md", ".mdx"}) List<String> fileExtensions,
            @DefaultValue("/tmp/documentation-analysis") Path copyLocation,
            @DefaultValue("24h") Duration maxCopyAge,
            @DefaultValue("50MB") DataSize maxCopySize,
            @DefaultValue("5000") int maxFiles,
            @DefaultValue("2") int maxAreasPerTicket) {

        private static final String GITHUB_PREFIX = PREFIX + "source.github.";
        private static final String WHEN_SELECTED = " when " + DocumentationSourceType.PROPERTY + "=github";

        public GitHub {
            repository = repository.trim();
            branch = branch.trim();
            path = trimSlashes(path.trim());
            fileExtensions = fileExtensions.stream()
                    .map(extension -> extension.trim().toLowerCase(Locale.ROOT))
                    .toList();
        }

        void validate() {
            if (repository.isEmpty()) {
                throw new IllegalArgumentException(GITHUB_PREFIX + "repository is required" + WHEN_SELECTED);
            }
            String[] parts = repository.split("/", -1);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new IllegalArgumentException(
                        GITHUB_PREFIX + "repository must be in owner/name format, got: " + repository);
            }
            if (branch.isEmpty()) {
                throw new IllegalArgumentException(GITHUB_PREFIX + "branch is required" + WHEN_SELECTED);
            }
            if (path.isEmpty()) {
                throw new IllegalArgumentException(GITHUB_PREFIX + "path is required" + WHEN_SELECTED);
            }
            for (String segment : path.split("/", -1)) {
                if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                    throw new IllegalArgumentException(GITHUB_PREFIX
                            + "path must be a relative directory without empty, '.' or '..' segments, got: " + path);
                }
            }
            if (fileExtensions.isEmpty()) {
                throw new IllegalArgumentException(GITHUB_PREFIX + "file-extensions must not be empty" + WHEN_SELECTED);
            }
            for (String extension : fileExtensions) {
                if (extension.length() < 2 || !extension.startsWith(".")) {
                    throw new IllegalArgumentException(
                            GITHUB_PREFIX + "file-extensions must each start with '.', got: " + extension);
                }
            }
            if (!copyLocation.isAbsolute()) {
                throw new IllegalArgumentException(
                        GITHUB_PREFIX + "copy-location must be an absolute path, got: " + copyLocation);
            }
            requirePositive(maxCopyAge, "source.github.max-copy-age");
            if (maxCopySize.toBytes() <= 0) {
                throw new IllegalArgumentException(GITHUB_PREFIX + "max-copy-size must be positive");
            }
            requirePositive(maxFiles, "source.github.max-files");
            requirePositive(maxAreasPerTicket, "source.github.max-areas-per-ticket");
        }

        private static String trimSlashes(String value) {
            int start = 0;
            int end = value.length();
            while (start < end && value.charAt(start) == '/') {
                start++;
            }
            while (end > start && value.charAt(end - 1) == '/') {
                end--;
            }
            return value.substring(start, end);
        }
    }

    private static void requirePositive(int value, String setting) {
        if (value <= 0) {
            throw new IllegalArgumentException(PREFIX + setting + " must be positive");
        }
    }

    private static void requirePositive(Duration value, String setting) {
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(PREFIX + setting + " must be positive");
        }
    }
}
