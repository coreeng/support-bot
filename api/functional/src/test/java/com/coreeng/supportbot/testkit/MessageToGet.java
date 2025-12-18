package com.coreeng.supportbot.testkit;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import org.jspecify.annotations.Nullable;

@Builder
@Getter
public class MessageToGet {
    @NonNull
    private final String channelId;
    @NonNull
    private final MessageTs ts;
    @NonNull
    private final MessageTs threadTs;
    @NonNull
    @Builder.Default
    private final String text = "";

    // Either userId or botId is not null
    @Nullable
    private final String userId;
    @Nullable
    private final String botId;

    @NonNull
    private final String team;
    @NonNull
    private final String blocksJson;
}
