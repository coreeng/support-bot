package com.coreeng.supportbot.testkit;

import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Builder
@Getter
public class MessageToPost {
    @NonNull
    private final String userId;
    @NonNull
    private final String teamId;
    @NonNull
    private final String channelId;
    @NonNull
    private final String message;
    @NonNull
    private final MessageTs ts;
}
