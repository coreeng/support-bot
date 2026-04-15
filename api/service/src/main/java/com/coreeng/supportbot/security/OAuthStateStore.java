package com.coreeng.supportbot.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class OAuthStateStore {
    private static final Duration STATE_EXPIRY = Duration.ofMinutes(10);

    private final Cache<String, Boolean> pendingStates = Caffeine.newBuilder()
            .expireAfterWrite(STATE_EXPIRY)
            .maximumSize(50_000)
            .build();

    public void store(String state) {
        pendingStates.put(state, Boolean.TRUE);
    }

    /** Consumes the state (one-time use). Returns true if the state was valid and not yet consumed. */
    public boolean consumeIfValid(@Nullable String state) {
        if (state == null || state.isBlank()) {
            return false;
        }
        var present = pendingStates.getIfPresent(state);
        if (present != null) {
            pendingStates.invalidate(state);
            return true;
        }
        return false;
    }
}
