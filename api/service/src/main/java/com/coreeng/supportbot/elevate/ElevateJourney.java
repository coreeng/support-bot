package com.coreeng.supportbot.elevate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ElevateJourney(
        String id,
        String slug,
        String name,
        String productId,
        String productSlug,
        @Nullable String userDescription,
        @Nullable String primaryProblems,
        List<UUID> userIds,
        LocalDateTime createdAt,
        LocalDateTime lastUpdatedAt) {

    public ElevateJourney {
        userIds = List.copyOf(userIds);
    }
}
