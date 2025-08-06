package com.coreeng.supportbot.testkit;

import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.NonNull;

@Builder(toBuilder = true)
@Getter
public class ThreadMessagePostedExpectation<T> {
    private final StubWithResult.@NonNull Receiver<T> receiver;
    @NonNull
    private final UserRole from;
    @NonNull
    private final String newMessageTs;

    private final String channelId;
    private final String threadTs;
}
