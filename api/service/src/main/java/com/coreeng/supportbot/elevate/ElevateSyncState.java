package com.coreeng.supportbot.elevate;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record ElevateSyncState(
        @Nullable Instant lastPingAttemptAt,
        @Nullable Instant lastPingSuccessAt,
        @Nullable Boolean lastPingSucceeded,
        @Nullable String lastPingError,
        @Nullable Instant lastSyncAttemptAt,
        @Nullable Instant lastSyncSuccessAt,
        @Nullable Boolean lastSyncSucceeded,
        @Nullable String lastSyncError) {

    public static ElevateSyncState empty() {
        return new ElevateSyncState(null, null, null, null, null, null, null, null);
    }
}
