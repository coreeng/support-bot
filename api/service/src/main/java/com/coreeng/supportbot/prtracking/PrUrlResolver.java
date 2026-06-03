package com.coreeng.supportbot.prtracking;

import com.coreeng.supportbot.config.PrTrackingProps;
import com.coreeng.supportbot.prtracking.source.Provider;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Single source of truth for the public-facing URL of every tracked repo, plus the GitLab MR-link
 * prefixes the URL parser matches against.
 *
 * <p>Built once at startup from {@link PrTrackingProps}; lookups are O(1). Keeps the
 * apiBaseUrl→public-URL derivation in one place so message rendering, in-flight URL building, and
 * URL parsing can't drift.
 */
public class PrUrlResolver {

    private final Map<String, PublicCoordinates> coordsByRepoName;
    private final Map<String, String> gitLabRepoByUrlPrefix;

    public PrUrlResolver(PrTrackingProps props) {
        Map<String, PublicCoordinates> coords = new HashMap<>();
        Map<String, String> gitLabPrefixes = new HashMap<>();
        for (PrTrackingProps.Repository repo : props.repositories()) {
            PublicCoordinates resolved = resolveFor(repo, props.gitlab());
            String canonicalName = repo.name().toLowerCase(Locale.ROOT);
            coords.put(canonicalName, resolved);
            if (repo.provider() == Provider.GITLAB) {
                gitLabPrefixes.put(urlPrefixOf(resolved.baseUrl(), repo.name()), canonicalName);
            }
        }
        this.coordsByRepoName = Map.copyOf(coords);
        this.gitLabRepoByUrlPrefix = Map.copyOf(gitLabPrefixes);
    }

    /**
     * Builds the public URL for a PR/MR by repo name (provider taken from config so callers don't
     * have to thread it through). Falls back to {@code https://github.com/...} for unknown repos to
     * keep behaviour identical to the previous hardcoded builder for non-configured rows.
     */
    public String publicUrlFor(String repoName, int prNumber) {
        PublicCoordinates coords = coordsByRepoName.get(repoName.toLowerCase(Locale.ROOT));
        if (coords == null) {
            return "https://github.com/%s/pull/%d".formatted(repoName, prNumber);
        }
        return switch (coords.provider()) {
            case GITHUB -> coords.baseUrl() + "/" + repoName + "/pull/" + prNumber;
            case GITLAB -> coords.baseUrl() + "/" + repoName + "/-/merge_requests/" + prNumber;
        };
    }

    /**
     * Maps each GitLab repo's normalized MR-link prefix — {@code host[/base-path]/project}, lowercased
     * and scheme-stripped — to its canonical (lowercased) repo name. {@link GitLabMrUrlParser} matches
     * the whole prefix rather than host and project path separately, which is what lets self-hosted
     * instances served under a base path (e.g. {@code https://example.com/gitlab/group/project}) resolve
     * and keeps matching case-insensitive on the project path.
     */
    public Map<String, String> gitLabRepoPrefixes() {
        return gitLabRepoByUrlPrefix;
    }

    /** Public URL to the repo's landing page, used by message templates as {@code repo_url}. */
    public String repoUrl(String repoName) {
        PublicCoordinates coords = coordsByRepoName.get(repoName.toLowerCase(Locale.ROOT));
        if (coords == null) {
            return "https://github.com/" + repoName;
        }
        return coords.baseUrl() + "/" + repoName;
    }

    private static PublicCoordinates resolveFor(
            PrTrackingProps.Repository repo, PrTrackingProps.@org.jspecify.annotations.Nullable Gitlab globalGitlab) {
        if (repo.provider() == Provider.GITHUB) {
            return new PublicCoordinates(Provider.GITHUB, "https://github.com");
        }
        String apiBaseUrl = "";
        PrTrackingProps.Gitlab perRepo = repo.gitlab();
        if (perRepo != null) {
            String url = perRepo.apiBaseUrl();
            if (url != null && !url.isBlank()) {
                apiBaseUrl = url;
            }
        }
        if (apiBaseUrl.isBlank() && globalGitlab != null) {
            String url = globalGitlab.apiBaseUrl();
            if (url != null && !url.isBlank()) {
                apiBaseUrl = url;
            }
        }
        if (apiBaseUrl.isBlank()) {
            // PrTrackingProps validation guarantees a resolvable gitlab block for every GitLab repo,
            // so this is only reachable from a misconfigured test fixture.
            throw new IllegalStateException("No gitlab.api-base-url resolvable for repo " + repo.name());
        }
        return new PublicCoordinates(Provider.GITLAB, stripApiV4(apiBaseUrl));
    }

    private static String stripApiV4(String apiBaseUrl) {
        int idx = apiBaseUrl.indexOf("/api/v4");
        return idx < 0 ? apiBaseUrl : apiBaseUrl.substring(0, idx);
    }

    /**
     * Normalized prefix a public MR link must start with to belong to {@code repoName}:
     * the public base URL with its scheme stripped, joined to the project path, lowercased — e.g.
     * {@code https://example.com/gitlab} + {@code group/project} → {@code example.com/gitlab/group/project}.
     * Base URLs are operator-authored and validated by PrTrackingProps, so a cheap scheme-strip is enough.
     */
    private static String urlPrefixOf(String baseUrl, String repoName) {
        int schemeEnd = baseUrl.indexOf("://");
        String hostAndPath = schemeEnd < 0 ? baseUrl : baseUrl.substring(schemeEnd + 3);
        if (hostAndPath.endsWith("/")) {
            hostAndPath = hostAndPath.substring(0, hostAndPath.length() - 1);
        }
        return (hostAndPath + "/" + repoName).toLowerCase(Locale.ROOT);
    }

    private record PublicCoordinates(Provider provider, String baseUrl) {}
}
