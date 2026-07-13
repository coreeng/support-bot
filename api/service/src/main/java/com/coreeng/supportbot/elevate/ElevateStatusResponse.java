package com.coreeng.supportbot.elevate;

import java.time.Instant;
import java.util.List;
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
        List<ElevateProduct> products,
        List<ElevateJourney> journeys,
        List<ElevateUser> users) {

    public ElevateStatusResponse {
        products = List.copyOf(products);
        journeys = List.copyOf(journeys);
        users = List.copyOf(users);
    }
}
