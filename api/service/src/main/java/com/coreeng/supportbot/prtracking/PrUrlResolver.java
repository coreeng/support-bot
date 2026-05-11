package com.coreeng.supportbot.prtracking;

import com.coreeng.supportbot.config.PrTrackingProps;
import com.coreeng.supportbot.prtracking.source.Provider;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Single source of truth for the public-facing URL of every tracked repo, plus the GitLab host
 * allow-list the URL parser uses.
 *
 * <p>Built once at startup from {@link PrTrackingProps}; lookups are O(1). Keeps the
 * apiBaseUrl→public-host derivation in one place so message rendering, in-flight URL building, and
 * URL parsing can't drift.
 */
public class PrUrlResolver {

    private final Map<String, PublicCoordinates> coordsByRepoName;
    private final Set<String> gitLabHosts;

    public PrUrlResolver(PrTrackingProps props) {
        Map<String, PublicCoordinates> coords = new HashMap<>();
        Set<String> hosts = new HashSet<>();
        for (PrTrackingProps.Repository repo : props.repositories()) {
            PublicCoordinates resolved = resolveFor(repo, props.gitlab());
            coords.put(repo.name().toLowerCase(Locale.ROOT), resolved);
            if (repo.provider() == Provider.GITLAB) {
                hosts.add(hostOf(resolved.baseUrl()));
            }
        }
        this.coordsByRepoName = Map.copyOf(coords);
        this.gitLabHosts = Set.copyOf(hosts);
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

    /** Hosts (e.g. {@code gitlab.com}, {@code gitlab.internal.example}) of every configured GitLab repo. */
    public Set<String> gitLabHosts() {
        return gitLabHosts;
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

    private static String hostOf(String baseUrl) {
        // Cheap path-strip parser — base URLs are operator-authored and validated by PrTrackingProps,
        // so URI.create() would be overkill and forces nullable handling for .getHost().
        int schemeEnd = baseUrl.indexOf("://");
        int hostStart = schemeEnd < 0 ? 0 : schemeEnd + 3;
        int pathStart = baseUrl.indexOf('/', hostStart);
        return pathStart < 0 ? baseUrl.substring(hostStart) : baseUrl.substring(hostStart, pathStart);
    }

    private record PublicCoordinates(Provider provider, String baseUrl) {}
}
