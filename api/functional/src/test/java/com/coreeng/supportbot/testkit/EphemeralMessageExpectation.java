package com.coreeng.supportbot.testkit;

import lombok.Builder;
import org.jspecify.annotations.NonNull;

@Builder
public record EphemeralMessageExpectation(
    @NonNull String channelId,
    @NonNull MessageTs threadTs,
    @NonNull String userId
) {
}

