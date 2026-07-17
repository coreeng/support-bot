package com.coreeng.supportbot.elevate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ElevateProduct(
        String id,
        String slug,
        String name,
        @Nullable String customer,
        LocalDateTime createdAt,
        LocalDateTime lastUpdatedAt) {}
