package com.coreeng.supportbot.testkit;

import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.NonNull;

@Builder(toBuilder = true)
@Getter
public class MessageUpdatedExpectation<T> {
    private final StubWithResult.@NonNull Receiver<T> receiver;
    @NonNull
    private final String channelId;
    @NonNull
    private final MessageTs ts;
    @NonNull
    private final MessageTs threadTs;
}
