package com.coreeng.supportbot.prtracking.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GitLabGroupMemberCacheTest {

    private final GitLabGroupMemberCache cache = new GitLabGroupMemberCache(Duration.ofMinutes(5));

    @Test
    void invokesLoaderOnMissAndCachesResult() {
        AtomicInteger loadCount = new AtomicInteger();

        List<String> first = cache.getMembers("https://gitlab.com/api/v4", "my-group", () -> {
            loadCount.incrementAndGet();
            return List.of("alice", "bob");
        });
        List<String> second = cache.getMembers("https://gitlab.com/api/v4", "my-group", () -> {
            loadCount.incrementAndGet();
            return List.of("CHANGED");
        });

        assertThat(first).containsExactly("alice", "bob");
        assertThat(second).isSameAs(first);
        assertThat(loadCount).hasValue(1);
    }

    @Test
    void keysIndependentlyByApiBaseUrlAndGroupPath() {
        AtomicInteger loadCount = new AtomicInteger();

        cache.getMembers("https://gitlab.com/api/v4", "g1", () -> {
            loadCount.incrementAndGet();
            return List.of("a");
        });
        cache.getMembers("https://gitlab.com/api/v4", "g2", () -> {
            loadCount.incrementAndGet();
            return List.of("b");
        });
        cache.getMembers("https://self.example/api/v4", "g1", () -> {
            loadCount.incrementAndGet();
            return List.of("c");
        });

        assertThat(loadCount).hasValue(3);
    }

    @Test
    void doesNotCacheFailedLoads() {
        // Caffeine's loader-throw semantics: the entry is not stored, so the next call retries.
        // Useful for transient GitLab 5xx — we don't want to memoize a one-off failure for the TTL.
        AtomicInteger loadCount = new AtomicInteger();

        assertThatThrownBy(() -> cache.getMembers("https://gitlab.com/api/v4", "g", () -> {
                    loadCount.incrementAndGet();
                    throw new GitLabApiException(500, "boom");
                }))
                .isInstanceOf(GitLabApiException.class);

        cache.getMembers("https://gitlab.com/api/v4", "g", () -> {
            loadCount.incrementAndGet();
            return List.of("alice");
        });

        assertThat(loadCount).hasValue(2);
    }

    @Test
    void rejectsNonPositiveTtl() {
        assertThatThrownBy(() -> new GitLabGroupMemberCache(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GitLabGroupMemberCache(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
