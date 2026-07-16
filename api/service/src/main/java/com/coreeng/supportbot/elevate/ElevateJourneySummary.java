package com.coreeng.supportbot.elevate;

import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

public record ElevateJourneySummary(
        String id,
        String slug,
        String name,
        String productId,
        String productSlug,
        @Nullable String userDescription,
        @Nullable String primaryProblems,
        LocalDateTime createdAt,
        LocalDateTime lastUpdatedAt,
        long userCount,
        long missingUserCount,
        long crossProductUserCount) {}
