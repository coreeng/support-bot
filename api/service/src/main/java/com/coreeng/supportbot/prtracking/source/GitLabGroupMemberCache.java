package com.coreeng.supportbot.prtracking.source;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Caches the result of {@code GET /groups/:id/members/all} per (apiBaseUrl, groupPath).
 *
 * <p>Sized for the operator-authored repository list — a thousand entries is plenty even with
 * per-repo apiBaseUrl overrides pointing at different self-hosted instances. TTL is supplied by
 * the caller (commit 3's plan reuses {@code pr-review-tracking.sla-discovery.cache}).
 *
 * <p>The cache does not perform HTTP itself; the caller passes a loader. Keeps the Caffeine
 * dependency confined here and lets the source client own all URL/auth concerns.
 */
public class GitLabGroupMemberCache {

    private static final Logger LOG = LoggerFactory.getLogger(GitLabGroupMemberCache.class);

    private final Cache<Key, List<String>> cache;

    public GitLabGroupMemberCache(Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive, was " + ttl);
        }
        this.cache =
                Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(1_000).build();
    }

    /**
     * Returns the cached membership list, invoking {@code loader} on a miss. Loader exceptions
     * propagate; failed loads are not cached, so the next call retries.
     */
    public List<String> getMembers(String apiBaseUrl, String groupPath, Supplier<List<String>> loader) {
        Key key = new Key(apiBaseUrl, groupPath);
        boolean[] loaded = {false};
        List<String> members = Objects.requireNonNull(
                cache.get(key, k -> {
                    loaded[0] = true;
                    LOG.debug(
                            "GitLab group members cache miss for group '{}' at {} — fetching from API",
                            groupPath,
                            apiBaseUrl);
                    return loader.get();
                }),
                "loader returned null member list");
        if (!loaded[0]) {
            LOG.debug("GitLab group members cache hit for group '{}' at {}", groupPath, apiBaseUrl);
        }
        return members;
    }

    private record Key(String apiBaseUrl, String groupPath) {}
}
