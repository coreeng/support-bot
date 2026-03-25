package com.coreeng.supportbot.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig {
    @Bean
    public CacheManager cacheManager() {
        return new CaffeineCacheManager();
    }

    @Bean("permalink-cache")
    public Cache permalinkCache() {
        return caffeineCache("permalink", 10_000);
    }

    @Bean("slack-user-cache")
    public Cache slackUserCache() {
        return caffeineCache("slack-user", 10_000);
    }

    @Bean("slack-group-cache")
    public Cache slackGroupCache() {
        return caffeineCache("slack-group", 1_000);
    }

    @Bean("slack-channel-cache")
    public Cache slackChannelCache() {
        return caffeineCache("slack-channel", 1_000);
    }

    // Single-entry cache: stores the full active-tags list under one key.
    // Uses expireAfterWrite (not expireAfterAccess) so tags refresh daily
    // regardless of read frequency.
    @Bean("tags-cache")
    public Cache tagsCache() {
        return new CaffeineCache(
                "tags",
                Caffeine.newBuilder()
                        .expireAfterWrite(1, TimeUnit.DAYS)
                        .maximumSize(1)
                        .recordStats()
                        .build());
    }

    private static CaffeineCache caffeineCache(String name, long maximumSize) {
        return new CaffeineCache(
                name,
                Caffeine.newBuilder()
                        .expireAfterAccess(1, TimeUnit.DAYS)
                        .maximumSize(maximumSize)
                        .recordStats()
                        .build());
    }

    @Bean
    public MeterBinder caffeineCacheMetrics(List<CaffeineCache> caches) {
        return registry -> {
            for (var cache : caches) {
                CaffeineCacheMetrics.monitor(registry, cache.getNativeCache(), cache.getName());
            }
        };
    }
}
