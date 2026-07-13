package com.coreeng.supportbot.elevate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ElevateUser(
        UUID id,
        String productId,
        String name,
        String description,
        LocalDateTime createdAt,
        LocalDateTime lastUpdatedAt) {}
