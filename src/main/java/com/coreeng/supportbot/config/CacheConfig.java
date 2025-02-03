package com.coreeng.supportbot.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
