package com.coreeng.supportbot.elevate;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ElevateStatusResponse(
        boolean configured,
        @Nullable String baseUrl,
        String statusInterval,
        String syncInterval,
        @Nullable Instant lastPingAttemptAt,
        @Nullable Instant lastPingSuccessAt,
        @Nullable Boolean lastPingSucceeded,
        @Nullable String lastPingError,
        @Nullable Instant lastSyncAttemptAt,
        @Nullable Instant lastSyncSuccessAt,
        @Nullable Boolean lastSyncSucceeded,
        @Nullable String lastSyncError,
        @Nullable UUID snapshotVersion,
        ElevateCounts counts,
        ElevateIntegrityCounts integrity) {}
