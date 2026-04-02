package com.coreeng.supportbot.config;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Name;

@ConfigurationProperties(prefix = "pr-review-tracking")
public record PrTrackingProps(
        boolean enabled,
        String pollCron,
        String prEmoji,
        List<String> tags,
        String impact,
        @Name("duration-unit") String durationUnit,
        List<Repository> repositories,
        GitHub github,
        SlaDiscovery slaDiscovery) {

    private static final Set<String> VALID_DURATION_UNITS = Set.of("hours", "days", "weeks");

    public PrTrackingProps(
            boolean enabled,
            String pollCron,
            @Nullable String prEmoji,
            @Nullable List<String> tags,
            @Nullable String impact,
            @Nullable @Name("duration-unit") String durationUnit,
            @Nullable List<Repository> repositories,
            @Nullable GitHub github,
            @Nullable SlaDiscovery slaDiscovery) {
        this.enabled = enabled;
        this.pollCron = pollCron;
        this.durationUnit = durationUnit == null ? "days" : durationUnit.toLowerCase(Locale.ROOT);
        this.prEmoji = prEmoji == null ? "pr" : prEmoji;
        this.tags = tags == null ? List.of() : List.copyOf(tags);
        this.impact = impact == null ? "" : impact;
        this.repositories = repositories == null
                ? List.of()
                : repositories.stream()
                        .map(repository -> new Repository(
                                normalizeRepositoryName(repository.name()),
                                repository.owningTeam(),
                                repository.githubTeamSlug(),
                                repository.sla()))
                        .toList();
        this.slaDiscovery = slaDiscovery == null ? new SlaDiscovery(null) : slaDiscovery;
        this.github = github == null ? GitHub.defaultTokenModeConfig() : github;

        if (enabled) {
            if (!VALID_DURATION_UNITS.contains(this.durationUnit)) {
                throw new IllegalArgumentException(
                        "pr-review-tracking.duration-unit must be one of [hours, days, weeks], got: "
                                + this.durationUnit);
            }
            requireNotBlank(this.prEmoji, "pr-review-tracking.pr-emoji must not be blank");
            if (this.tags.isEmpty()) {
                throw new IllegalArgumentException("pr-review-tracking.tags must not be empty when enabled");
            }
            if (isBlank(this.impact)) {
                throw new IllegalArgumentException("pr-review-tracking.impact must not be blank when enabled");
            }
            if (github == null) {
                throw new IllegalArgumentException("pr-review-tracking.github must be configured when enabled");
            }
            validateRepositories(this.repositories);
            validateConfig(this.github);
        }
    }

    private static void validateRepositories(List<Repository> repositories) {
        if (repositories.isEmpty()) {
            throw new IllegalArgumentException("pr-review-tracking.repositories must not be empty when enabled");
        }
        Set<String> names = new HashSet<>();
        for (Repository repository : repositories) {
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
            if (repository.sla() == null) {
                throw new IllegalArgumentException("pr-review-tracking.repositories[].sla must not be null");
            }
            Duration defaultSla = repository.sla().defaultSla();
            boolean hasFile = !isBlank(repository.sla().file());
            if (!hasFile && defaultSla == null) {
                throw new IllegalArgumentException(
                        "pr-review-tracking.repositories[].sla.default must be set when sla.file is not configured");
            }
            if (defaultSla != null && (defaultSla.isZero() || defaultSla.isNegative())) {
                throw new IllegalArgumentException(
                        "pr-review-tracking.repositories[].sla.default must be a positive duration");
            }
            List<SlaOverride> overrides = repository.sla().overrides();
            for (SlaOverride override : overrides != null ? overrides : List.<SlaOverride>of()) {
                if (isBlank(override.path())) {
                    throw new IllegalArgumentException(
                            "pr-review-tracking.repositories[].sla.overrides[].path must not be blank");
                }
                if (override.sla().isZero() || override.sla().isNegative()) {
                    throw new IllegalArgumentException(
                            "pr-review-tracking.repositories[].sla.overrides[].sla must be a positive duration");
                }
            }

            String normalizedName = repository.name().toLowerCase(Locale.ROOT);

            if (!names.add(normalizedName)) {
                throw new IllegalArgumentException(
                        "pr-review-tracking.repositories[].name contains duplicates: " + repository.name());
            }
        }
    }

    private static void validateConfig(GitHub githubConfig) {
        if (githubConfig.authMode() == null) {
            throw new IllegalArgumentException("pr-review-tracking.github.auth-mode must not be blank when enabled");
        }
        if (isBlank(githubConfig.apiBaseUrl())) {
            throw new IllegalArgumentException("pr-review-tracking.github.api-base-url must not be blank");
        }
        if (githubConfig.authMode() == AuthMode.APP) {
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

    private static String normalizeRepositoryName(String repositoryName) {
        return repositoryName == null ? "" : repositoryName.toLowerCase(Locale.ROOT);
    }

    public enum AuthMode {
        TOKEN,
        APP
    }

    public record Repository(
            String name, String owningTeam, @Nullable String githubTeamSlug, Sla sla) {
        public Repository {
            requireNonNull(name, "name must not be null");
            requireNonNull(owningTeam, "owningTeam must not be null");
            if (githubTeamSlug != null && githubTeamSlug.isBlank()) {
                throw new IllegalArgumentException("githubTeamSlug must not be blank when provided");
            }
            requireNonNull(sla, "sla must not be null");
        }
    }

    public record Sla(
            @Nullable String file,
            @Name("default") @Nullable Duration defaultSla,
            @Nullable List<SlaOverride> overrides) {

        public Sla {
            overrides = overrides == null ? List.of() : List.copyOf(overrides);
        }
    }

    public record SlaOverride(String path, Duration sla) {
        public SlaOverride {
            requireNonNull(path, "path must not be null");
            requireNonNull(sla, "sla must not be null");
        }
    }

    public record SlaDiscovery(@Nullable Duration cache) {
        public SlaDiscovery {
            cache = cache == null ? Duration.ofHours(24) : cache;
            if (cache.isZero() || cache.isNegative()) {
                throw new IllegalArgumentException("slaDiscovery.cache must be a positive duration");
            }
        }
    }

    public record GitHub(
            @Nullable AuthMode authMode,
            String apiBaseUrl,
            String token,
            String appId,
            String installationId,
            String privateKeyPem) {

        public GitHub {
            apiBaseUrl = nonNullString(apiBaseUrl);
            token = nonNullString(token);
            appId = nonNullString(appId);
            installationId = nonNullString(installationId);
            privateKeyPem = nonNullString(privateKeyPem);
        }

        public static GitHub defaultTokenModeConfig() {
            return new GitHub(AuthMode.TOKEN, "", "", "", "", "");
        }

        @Override
        public String toString() {
            return "GitHub[authMode=" + authMode
                    + ", apiBaseUrl=" + apiBaseUrl
                    + ", token=REDACTED"
                    + ", appId=" + appId
                    + ", installationId=" + installationId
                    + ", privateKeyPem=REDACTED]";
        }

        private static String nonNullString(@Nullable String value) {
            return value == null ? "" : value;
        }
    }
}
