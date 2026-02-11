package com.coreeng.supportbot.testkit;

import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.NonNull;

@Builder(toBuilder = true)
@Getter
public class ThreadMessagePostedExpectation<T> {
    @NonNull private final String description;

    private final StubWithResult.@NonNull Receiver<T> receiver;

    @NonNull private final UserRole from;

    @NonNull private final MessageTs newMessageTs;

    private final String channelId;
    private final MessageTs threadTs;
}
