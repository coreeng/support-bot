package com.coreeng.supportbot.testkit;

import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

@Builder
@Getter
public class ConversationRepliesToGet {
    private final String channelId;
    private final MessageTs ts;
    private final MessageTs threadTs;
    @Nullable
    private final MessageTs reply;
}
