package com.coreeng.supportbot.testkit;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

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
    private final String user;
    @NonNull
    private final String team;
    @NonNull
    private final String blocksJson;
}
