package com.coreeng.supportbot.testkit;

import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.NonNull;

@Builder
@Getter
public class EphemeralMessageExpectation<T> {
    @NonNull private final String description;

    @NonNull private final String channelId;

    @NonNull private final MessageTs threadTs;

    @NonNull private final String userId;

    private final StubWithResult.@NonNull Receiver<T> receiver;
}
