package com.coreeng.supportbot.config;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pr-review-tracking")
public record PrTrackingProps(
        boolean enabled,
        String pollCron,
        String prEmoji,
        List<String> tags,
        String impact,
        List<PrTrackingRepositoryProps> repositories,
        PrTrackingGitHubProps github) {

    public PrTrackingProps(
            boolean enabled,
            String pollCron,
            @Nullable String prEmoji,
            @Nullable List<String> tags,
            @Nullable String impact,
            @Nullable List<PrTrackingRepositoryProps> repositories,
            @Nullable PrTrackingGitHubProps github) {
        this.enabled = enabled;
        this.pollCron = pollCron;
        this.prEmoji = prEmoji == null ? "pr" : prEmoji;
        this.tags = tags == null ? List.of() : List.copyOf(tags);
        this.impact = impact == null ? "" : impact;
        this.repositories = repositories == null ? List.of() : List.copyOf(repositories);
        this.github = github == null ? PrTrackingGitHubProps.defaultTokenModeConfig() : github;

        if (enabled) {
            requireNotBlank(this.prEmoji, "pr-review-tracking.pr-emoji must not be blank");
            if (this.tags.isEmpty()) {
                throw new IllegalArgumentException("pr-review-tracking.tags must not be empty when enabled");
            }
            if (isBlank(this.impact)) {
                throw new IllegalArgumentException("pr-review-tracking.impact must not be blank when enabled");
            }
            validateRepositories(this.repositories);
            validateConfig(this.github);
        }
    }

    private static void validateRepositories(List<PrTrackingRepositoryProps> repositories) {
        if (repositories.isEmpty()) {
            throw new IllegalArgumentException("pr-review-tracking.repositories must not be empty when enabled");
        }
        Set<String> names = new HashSet<>();
        for (PrTrackingRepositoryProps repository : repositories) {
            if (isBlank(repository.name())) {
                throw new IllegalArgumentException("pr-review-tracking.repositories[].name must not be blank");
            }

            String[] repositoryParts = repository.name().split("/", -1);
            if (repositoryParts.length != 2 || repositoryParts[0].isBlank() || repositoryParts[1].isBlank()) {
                throw new IllegalArgumentException("pr-review-tracking.repositories[].name must be in org/repo format");
            }

            if (isBlank(repository.owningTeam())) {
                throw new IllegalArgumentException("pr-review-tracking.repositories[].owning-team must not be blank");
            }
            if (repository.sla() == null
                    || repository.sla().isZero()
                    || repository.sla().isNegative()) {
                throw new IllegalArgumentException("pr-review-tracking.repositories[].sla must be a positive duration");
            }
            validateTenantPathGlobs(repository);

            String normalizedName = repository.name().toLowerCase(Locale.ROOT);

            if (!names.add(normalizedName)) {
                throw new IllegalArgumentException(
                        "pr-review-tracking.repositories[].name contains duplicates: " + repository.name());
            }
        }
    }

    private static void validateTenantPathGlobs(PrTrackingRepositoryProps repository) {
        for (String tenantPathGlob : repository.tenantPathGlobs()) {
            if (isBlank(tenantPathGlob)) {
                throw new IllegalArgumentException(
                        "pr-review-tracking.repositories[].tenant-path-globs[] must not be blank");
            }
        }
    }

    private static void validateConfig(PrTrackingGitHubProps githubConfig) {
        if (isBlank(githubConfig.apiBaseUrl())) {
            throw new IllegalArgumentException("pr-review-tracking.github.api-base-url must not be blank");
        }
        if (githubConfig.authMode() == PrTrackingAuthMode.APP) {
            requireNotBlank(
                    githubConfig.appId(), "pr-review-tracking.github.app-id must not be blank when auth-mode=app");
            requireNotBlank(
                    githubConfig.installationId(),
                    "pr-review-tracking.github.installation-id must not be blank when auth-mode=app");
            requireNotBlank(
                    githubConfig.privateKeyPem(),
                    "pr-review-tracking.github.private-key-pem must not be blank when auth-mode=app");
            return;
        }
        requireNotBlank(githubConfig.token(), "pr-review-tracking.github.token must not be blank when auth-mode=token");
    }

    private static void requireNotBlank(String value, String errorMessage) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(errorMessage);
        }
    }
}
