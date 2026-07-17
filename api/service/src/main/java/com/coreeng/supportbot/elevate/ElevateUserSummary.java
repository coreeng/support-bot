package com.coreeng.supportbot.elevate;

import java.time.LocalDateTime;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ElevateUserSummary(
        UUID id,
        String productId,
        String name,
        @Nullable String description,
        LocalDateTime createdAt,
        LocalDateTime lastUpdatedAt,
        long journeyCount) {}
