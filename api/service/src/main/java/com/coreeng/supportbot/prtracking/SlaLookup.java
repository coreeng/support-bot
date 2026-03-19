package com.coreeng.supportbot.prtracking;

import com.coreeng.supportbot.config.PrTrackingProps;
import com.coreeng.supportbot.github.GitHubClient;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

@Service
@ConditionalOnProperty(name = "pr-review-tracking.enabled", havingValue = "true")
@Slf4j
public class SlaLookup {

    private final GitHubClient gitHubClient;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final Cache<String, Optional<ParsedSlaFile>> fileCache;

    public SlaLookup(GitHubClient gitHubClient, PrTrackingProps prTrackingProps) {
        this.gitHubClient = gitHubClient;
        Duration cacheTtl =
                Objects.requireNonNull(prTrackingProps.slaDiscovery().cache(), "slaDiscovery.cache must not be null");
        this.fileCache = Caffeine.newBuilder()
                .expireAfterWrite(cacheTtl)
                .maximumSize(1_000)
                .build();
    }

    /**
     * Expected YAML format for the SLA file in the repository:
     *
     * <pre>
     * default: 48h
     * overrides:
     *   - path: "infra/**"
     *     sla: 72h
     *   - path: "monitoring/**"
     *     sla: 24h
     * </pre>
     */
    private record RawSlaFile(
            @JsonProperty("default") @Nullable String defaultSla,
            @Nullable List<RawSlaOverrideEntry> overrides) {}

    private record RawSlaOverrideEntry(
            @Nullable String path, @Nullable String sla) {}

    /** SLA file with duration strings already parsed into Duration objects. */
    private record ParsedSlaFile(@Nullable Duration defaultSla, List<PrTrackingProps.SlaOverride> overrides) {}

    /**
     * Returns the SLA for a pull request. Repository file takes precedence over config values,
     * and path based overrides take precedence over the default. First matching override wins.
     *
     * @return the SLA, or null if none could be determined
     */
    @Nullable public Duration getSla(PrTrackingProps.Repository repoConfig, String repositoryName, int pullNumber) {
        PrTrackingProps.Sla configSla = repoConfig.sla();
        Duration defaultSla = configSla.defaultSla();
        List<PrTrackingProps.SlaOverride> overrides = configSla.overrides() != null ? configSla.overrides() : List.of();

        // If a file path is configured, fetch it (cached) and let it override config values
        String file = configSla.file();
        if (file != null && !file.isBlank()) {
            String cacheKey = repositoryName + ":" + file;
            Optional<ParsedSlaFile> cached;
            try {
                cached = fileCache.get(cacheKey, k -> fetchAndParse(repositoryName, file));
            } catch (InvalidSlaFileException e) {
                log.atWarn()
                        .addArgument(file)
                        .addArgument(repositoryName)
                        .addArgument(e::getMessage)
                        .log("Invalid SLA file {} in {}: {}. Using config instead.");
                cached = Optional.empty();
            }
            if (cached.isPresent()) {
                ParsedSlaFile parsed = cached.get();
                if (parsed.defaultSla() != null) {
                    defaultSla = parsed.defaultSla();
                }
                if (!parsed.overrides().isEmpty()) {
                    overrides = parsed.overrides();
                }
            }
        }

        Duration matched = matchOverride(overrides, repositoryName, pullNumber);
        return matched != null ? matched : defaultSla;
    }

    @Nullable private Duration matchOverride(List<PrTrackingProps.SlaOverride> overrides, String repositoryName, int pullNumber) {
        if (overrides.isEmpty()) {
            return null;
        }

        List<String> prFiles = gitHubClient.listPullRequestFiles(repositoryName, pullNumber);
        for (PrTrackingProps.SlaOverride override : overrides) {
            for (String prFile : prFiles) {
                if (pathMatcher.match(override.path(), prFile)) {
                    log.atDebug()
                            .addArgument(repositoryName)
                            .addArgument(pullNumber)
                            .addArgument(override::path)
                            .addArgument(override::sla)
                            .log("{}#{} matched override pattern {} → {}");
                    return override.sla();
                }
            }
        }
        return null;
    }

    /**
     * Fetches the SLA file from the repository, parses the YAML, and converts all duration strings
     * into Duration objects. Returns Optional.empty() if the file is not found (cached — safe sentinel).
     * Throws on parse/validation errors so Caffeine does not cache the failure.
     */
    private Optional<ParsedSlaFile> fetchAndParse(String repositoryName, String filePath) {
        String content = gitHubClient.getFileContent(repositoryName, filePath);
        if (content == null) {
            log.atInfo()
                    .addArgument(filePath)
                    .addArgument(repositoryName)
                    .log("SLA file {} not found in {}, using config");
            return Optional.empty();
        }

        RawSlaFile raw;
        try {
            raw = yamlMapper.readValue(content, RawSlaFile.class);
        } catch (JsonProcessingException e) {
            throw new InvalidSlaFileException(
                    "Failed to parse SLA file %s from %s".formatted(filePath, repositoryName), e);
        }

        if (raw.overrides() != null) {
            for (RawSlaOverrideEntry entry : raw.overrides()) {
                if (entry.path() == null || entry.sla() == null) {
                    throw new InvalidSlaFileException("SLA file %s from %s has override entry missing path or sla field"
                            .formatted(filePath, repositoryName));
                }
            }
        }

        try {
            Duration parsedDefault = raw.defaultSla() != null ? parseDuration(raw.defaultSla()) : null;

            List<PrTrackingProps.SlaOverride> parsedOverrides = List.of();
            if (raw.overrides() != null && !raw.overrides().isEmpty()) {
                parsedOverrides = raw.overrides().stream()
                        .map(e -> new PrTrackingProps.SlaOverride(
                                Objects.requireNonNull(e.path()), parseDuration(Objects.requireNonNull(e.sla()))))
                        .toList();
            }

            return Optional.of(new ParsedSlaFile(parsedDefault, parsedOverrides));
        } catch (IllegalArgumentException e) {
            throw new InvalidSlaFileException(
                    "Invalid values in SLA file %s from %s: %s".formatted(filePath, repositoryName, e.getMessage()), e);
        }
    }

    static class InvalidSlaFileException extends RuntimeException {
        InvalidSlaFileException(String message) {
            super(message);
        }

        InvalidSlaFileException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Parses SLA duration strings. Supports bare integers (interpreted as days), weeks (1w),
     * and anything Spring's DurationStyle handles (48h, 2d, PT48H).
     */
    private static Duration parseDuration(String value) {
        String trimmed = value.trim();
        Duration duration;
        if (trimmed.matches("\\d+")) {
            duration = Duration.ofDays(Long.parseLong(trimmed));
        } else if (trimmed.endsWith("w") || trimmed.endsWith("W")) {
            long weeks = Long.parseLong(trimmed.substring(0, trimmed.length() - 1));
            duration = Duration.ofDays(weeks * 7);
        } else {
            duration = DurationStyle.detectAndParse(trimmed);
        }
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("SLA duration must be positive, got: " + value);
        }
        return duration;
    }
}
