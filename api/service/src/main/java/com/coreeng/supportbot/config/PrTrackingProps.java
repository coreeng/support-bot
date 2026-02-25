package com.coreeng.supportbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.*;

import static org.apache.commons.lang3.StringUtils.isBlank;

@ConfigurationProperties(prefix = "pr-review-tracking")
public record PrTrackingProps(
        boolean enabled,
        String pollCron,
        List<PrTrackingRepositoryProps> repositories,
        PrTrackingGitHubProps githubConfig) {

    public PrTrackingProps {
        repositories = repositories == null ? List.of() : List.copyOf(repositories);
        githubConfig = githubConfig == null ? PrTrackingGitHubProps.defaultTokenModeConfig() : githubConfig;

        if (enabled) {
            validateRepositories(repositories);
            validateConfig(githubConfig);
        }
    }

    private static void validateRepositories(List<PrTrackingRepositoryProps> repositories) {
        Set<String> names = new HashSet<>();
        for (PrTrackingRepositoryProps repository : repositories) {
            if (isBlank(repository.name())) {
                throw new IllegalArgumentException("pr-review-tracking.repositories[].name must not be blank");
            }

            String[] repositoryParts = repository.name().split("/", -1);
            if (repositoryParts.length != 2 || repositoryParts[0].isBlank() || repositoryParts[1].isBlank()) {
                throw new IllegalArgumentException(
                        "pr-review-tracking.repositories[].name must be in org/repo format");
            }

            if (isBlank(repository.owningTeam())) {
                throw new IllegalArgumentException("pr-review-tracking.repositories[].owning-team must not be blank");
            }
            if (repository.sla() == null || repository.sla().isZero() || repository.sla().isNegative()) {
                throw new IllegalArgumentException("pr-review-tracking.repositories[].sla must be a positive duration");
            }

            String normalizedName = repository.name().toLowerCase(Locale.ROOT);

            if (!names.add(normalizedName)) {
                throw new IllegalArgumentException(
                        "pr-review-tracking.repositories[].name contains duplicates: " + repository.name());
            }
        }
    }

    private static void validateConfig(PrTrackingGitHubProps githubConfig) {
        if (isBlank(githubConfig.apiBaseUrl())) {
            throw new IllegalArgumentException("pr-review-tracking.github.api-base-url must not be blank");
        }
        if (githubConfig.authMode() == PrTrackingAuthMode.APP) {
            requireNotBlank(
                    githubConfig.appId(),
                    "pr-review-tracking.github.app-id must not be blank when auth-mode=app");
            requireNotBlank(
                    githubConfig.installationId(),
                    "pr-review-tracking.github.installation-id must not be blank when auth-mode=app");
            requireNotBlank(
                    githubConfig.privateKeyPem(),
                    "pr-review-tracking.github.private-key-pem must not be blank when auth-mode=app");
            return;
        }
        requireNotBlank(
                githubConfig.token(), "pr-review-tracking.github.token must not be blank when auth-mode=token");
    }

    private static void requireNotBlank(String value, String errorMessage) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(errorMessage);
        }
    }
}
