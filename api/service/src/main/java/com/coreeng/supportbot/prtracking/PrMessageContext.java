package com.coreeng.supportbot.prtracking;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Context variables available to CEL message templates. All fields are present for every event;
 * SLA-specific fields ({@code sla} and {@code slaDeadline}) are {@code null} for no-SLA repos.
 * Both must be null or both non-null — a mixed state is not valid.
 */
public record PrMessageContext(
        String repoName,
        int prNumber,
        String owningTeam,
        @Nullable Duration sla,
        @Nullable Instant slaDeadline) {

    public PrMessageContext {
        Objects.requireNonNull(repoName, "repoName");
        Objects.requireNonNull(owningTeam, "owningTeam");
        if ((sla == null) != (slaDeadline == null)) {
            throw new IllegalArgumentException("sla and slaDeadline must both be null or both be non-null");
        }
    }
}
