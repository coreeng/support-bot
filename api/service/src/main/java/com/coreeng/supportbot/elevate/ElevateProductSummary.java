package com.coreeng.supportbot.elevate;

import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

public record ElevateProductSummary(
        String id,
        String slug,
        String name,
        @Nullable String customer,
        LocalDateTime createdAt,
        LocalDateTime lastUpdatedAt,
        long journeyCount,
        long userCount,
        long assignmentCount) {}
