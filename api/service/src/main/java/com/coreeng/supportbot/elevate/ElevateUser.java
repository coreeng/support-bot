package com.coreeng.supportbot.elevate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ElevateUser(
        UUID id,
        String productId,
        String name,
        @Nullable String description,
        LocalDateTime createdAt,
        LocalDateTime lastUpdatedAt) {}
