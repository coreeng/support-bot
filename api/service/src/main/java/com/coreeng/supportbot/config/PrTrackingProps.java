package com.coreeng.supportbot.config;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.coreeng.supportbot.prtracking.source.Provider;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
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
        @Nullable Gitlab gitlab,
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
            @Nullable Gitlab gitlab,
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
                                repository.provider(),
                                repository.githubTeamSlug(),
                                repository.gitlabGroupPath(),
                                repository.paths(),
                                repository.sla(),
                                repository.gitlab(),
                                repository.messages()))
                        .toList();
        this.slaDiscovery = slaDiscovery == null ? new SlaDiscovery(null) : slaDiscovery;
        this.github = github == null ? GitHub.defaultTokenModeConfig() : github;
        this.gitlab = gitlab;

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
            validateRepositories(this.repositories, this.gitlab);
            validateConfig(this.github);
        }
    }

    private static void validateRepositories(List<Repository> repositories, @Nullable Gitlab globalGitlab) {
        if (repositories.isEmpty()) {
            throw new IllegalArgumentException("pr-review-tracking.repositories must not be empty when enabled");
        }
        Set<String> names = new HashSet<>();
        for (Repository repository : repositories) {
            if (isBlank(repository.name())) {
                throw new IllegalArgumentException("pr-review-tracking.repositories[].name must not be blank");
            }

            // GitHub requires exactly `org/repo`. GitLab permits nested groups
            // (`group/subgroup/project`), so any 2+ non-blank path segments are valid.
            String[] repositoryParts = repository.name().split("/", -1);
            boolean anyBlank = false;
            for (String part : repositoryParts) {
                if (part.isBlank()) {
                    anyBlank = true;
                    break;
                }
            }
            if (repository.provider() == Provider.GITHUB) {
                if (repositoryParts.length != 2 || anyBlank) {
                    throw new IllegalArgumentException(
                            "pr-review-tracking.repositories[].name must be in org/repo format for GitHub repos");
                }
            } else {
                if (repositoryParts.length < 2 || anyBlank) {
                    throw new IllegalArgumentException(
                            "pr-review-tracking.repositories[].name must contain at least one '/' (group/project or group/subgroup/project) for GitLab repos");
                }
            }

            if (isBlank(repository.owningTeam())) {
                throw new IllegalArgumentException("pr-review-tracking.repositories[].owning-team must not be blank");
            }

            if (repository.messages() != null) {
                validateMessages(repository.messages(), repository.sla() == null, repository.name());
            }

            // For each GitLab repo, the token must be resolvable. Resolved as: per-repo gitlab.token
            // wins; otherwise fall back to global gitlab.token. The api-base-url has the same precedence
            // but is enforced at use-time (commit 4) — here we only check that the credential is present.
            if (repository.provider() == Provider.GITLAB) {
                boolean perRepoToken = repository.gitlab() != null
                        && !isBlank(repository.gitlab().token());
                boolean globalToken = globalGitlab != null && !isBlank(globalGitlab.token());
                if (!perRepoToken && !globalToken) {
                    throw new IllegalArgumentException(
                            "pr-review-tracking.gitlab.token must be set (globally or per-repo) when any repo uses provider=gitlab: "
                                    + repository.name());
                }
            }

            if (repository.sla() == null) {
                // No-SLA repository: paths are required to filter which PRs are tracked (by changed-file matching)
                if (repository.paths().isEmpty()) {
                    throw new IllegalArgumentException(
                            "pr-review-tracking.repositories[].paths must not be empty when sla is not configured");
                }
                for (String path : repository.paths()) {
                    if (isBlank(path)) {
                        throw new IllegalArgumentException(
                                "pr-review-tracking.repositories[].paths[] must not be blank");
                    }
                }
            } else {
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

    private static void validateMessages(Messages messages, boolean isNoSlaRepo, String repoName) {
        if (messages.escalated() != null && isNoSlaRepo) {
            throw new IllegalArgumentException(
                    "pr-review-tracking.repositories[].messages.escalated must not be set for no-SLA repositories (repo: "
                            + repoName + ")");
        }
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
            String name,
            String owningTeam,
            Provider provider,
            @Nullable String githubTeamSlug,
            @Nullable String gitlabGroupPath,
            List<String> paths,
            @Nullable Sla sla,
            @Nullable Gitlab gitlab,
            @Nullable Messages messages) {
        @ConstructorBinding
        public Repository(
                String name,
                String owningTeam,
                @Nullable Provider provider,
                @Nullable String githubTeamSlug,
                @Nullable String gitlabGroupPath,
                @Nullable List<String> paths,
                @Nullable Sla sla,
                @Nullable Gitlab gitlab,
                @Nullable Messages messages) {
            requireNonNull(name, "name must not be null");
            requireNonNull(owningTeam, "owningTeam must not be null");
            Provider resolvedProvider = provider == null ? Provider.GITHUB : provider;
            if (githubTeamSlug != null && githubTeamSlug.isBlank()) {
                throw new IllegalArgumentException("githubTeamSlug must not be blank when provided");
            }
            if (gitlabGroupPath != null && gitlabGroupPath.isBlank()) {
                throw new IllegalArgumentException("gitlabGroupPath must not be blank when provided");
            }
            // Provider-scoped fields fail fast at config-bind time so a misconfigured repo never
            // makes it through to the adapter, where the mismatch would manifest as a confusing
            // runtime error (e.g. GitHub adapter receiving a gitlab-group-path).
            if (resolvedProvider == Provider.GITHUB) {
                if (gitlabGroupPath != null) {
                    throw new IllegalArgumentException(
                            "gitlabGroupPath is only valid when provider=gitlab (repo: " + name + ")");
                }
                if (gitlab != null) {
                    throw new IllegalArgumentException(
                            "per-repo gitlab override block is only valid when provider=gitlab (repo: " + name + ")");
                }
            } else if (resolvedProvider == Provider.GITLAB) {
                if (githubTeamSlug != null) {
                    throw new IllegalArgumentException(
                            "githubTeamSlug is only valid when provider=github (repo: " + name + ")");
                }
            }
            this.name = name;
            this.owningTeam = owningTeam;
            this.provider = resolvedProvider;
            this.githubTeamSlug = githubTeamSlug;
            this.gitlabGroupPath = gitlabGroupPath;
            this.paths = paths == null ? List.of() : List.copyOf(paths);
            this.sla = sla;
            this.gitlab = gitlab;
            this.messages = messages;
        }

        /** Test/legacy convenience: GitHub repo without GitLab fields. */
        public Repository(
                String name,
                String owningTeam,
                @Nullable String githubTeamSlug,
                List<String> paths,
                @Nullable Sla sla,
                @Nullable Messages messages) {
            this(name, owningTeam, Provider.GITHUB, githubTeamSlug, null, paths, sla, null, messages);
        }

        /** Test/legacy convenience: GitHub repo without messages or GitLab fields. */
        public Repository(
                String name,
                String owningTeam,
                @Nullable String githubTeamSlug,
                List<String> paths,
                @Nullable Sla sla) {
            this(name, owningTeam, Provider.GITHUB, githubTeamSlug, null, paths, sla, null, null);
        }

        /** Returns true when this repository has no SLA configured (no-SLA tracking mode). */
        public boolean hasNoSla() {
            return sla == null;
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

    public record Messages(
            @Nullable String detected,
            @Nullable String escalated,
            @Nullable String approved,
            @Name("changes-requested") @Nullable String changesRequested,
            @Nullable String merged,
            @Nullable String closed) {

        public Messages {
            checkBlank(detected, "detected");
            checkBlank(escalated, "escalated");
            checkBlank(approved, "approved");
            checkBlank(changesRequested, "changes-requested");
            checkBlank(merged, "merged");
            checkBlank(closed, "closed");
        }

        private static void checkBlank(@Nullable String value, String field) {
            if (value != null && value.isBlank()) {
                throw new IllegalArgumentException("messages." + field + " must not be blank when provided");
            }
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

    /**
     * GitLab connection settings — usable as the global {@code pr-review-tracking.gitlab} block or
     * as a per-repo override on {@link Repository#gitlab()}. Per-repo wins when both are set; that
     * resolution lives in the adapter (commit 4), not here.
     *
     * <p>{@code apiBaseUrl} shape is enforced at construction time so a malformed URL (trailing
     * slash, missing {@code /api/v4}) fails startup rather than surfacing as a confusing 404 on
     * the first poll.
     */
    public record Gitlab(
            @Nullable String apiBaseUrl, @Nullable String token) {

        public Gitlab(@Nullable String apiBaseUrl, @Nullable String token) {
            String normalisedApiBaseUrl = apiBaseUrl == null ? "" : apiBaseUrl;
            String normalisedToken = token == null ? "" : token;
            if (!normalisedApiBaseUrl.isBlank()) {
                if (normalisedApiBaseUrl.endsWith("/")) {
                    throw new IllegalArgumentException(
                            "gitlab.api-base-url must not end with a trailing slash, got: " + normalisedApiBaseUrl);
                }
                if (!normalisedApiBaseUrl.contains("/api/v4")) {
                    throw new IllegalArgumentException(
                            "gitlab.api-base-url must include the /api/v4 segment (no auto-append), got: "
                                    + normalisedApiBaseUrl);
                }
            }
            this.apiBaseUrl = normalisedApiBaseUrl;
            this.token = normalisedToken;
        }

        @Override
        public String toString() {
            return "Gitlab[apiBaseUrl=" + apiBaseUrl + ", token=REDACTED]";
        }
    }
}
