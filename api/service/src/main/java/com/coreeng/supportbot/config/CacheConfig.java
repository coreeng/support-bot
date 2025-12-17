package com.coreeng.supportbot.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {
    @Bean
    public CacheManager cacheManager() {
        return new CaffeineCacheManager();
    }

    @Bean("permalink-cache")
    public Cache permalinkCache() {
        return new CaffeineCache(
            "permalink",
            Caffeine.newBuilder()
                .expireAfterAccess(1, TimeUnit.DAYS)
                .maximumSize(10_000)
                .recordStats()
                .build()
        );
    }

    @Bean("slack-user-cache")
    public Cache slackUserCache() {
        return new CaffeineCache(
            "slack-user",
            Caffeine.newBuilder()
                .expireAfterAccess(1, TimeUnit.DAYS)
                .maximumSize(10_000)
                .recordStats()
                .build()
        );
    }

    @Bean("slack-group-cache")
    public Cache slackGroupCache() {
        return new CaffeineCache(
            "slack-group",
            Caffeine.newBuilder()
                .expireAfterAccess(1, TimeUnit.DAYS)
                .maximumSize(1_000)
                .recordStats()
                .build()
        );
    }

    @Bean("slack-channel-cache")
    public Cache slackChannelCache() {
        return new CaffeineCache(
            "slack-channel",
            Caffeine.newBuilder()
                .expireAfterAccess(1, TimeUnit.DAYS)
                .maximumSize(1_000)
                .recordStats()
                .build()
        );
    }

    @Bean
    public MeterBinder slackCacheMetrics(
        List<CaffeineCache> caches
    ) {
        return registry -> {
            for (var cache : caches) {
                CaffeineCacheMetrics.monitor(registry, cache.getNativeCache(), cache.getName());
            }
        };
    }
}
