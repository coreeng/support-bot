package com.coreeng.supportbot.testkit;

import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Builder
@Getter
public class ConversationRepliesToGet {
    @NonNull private final String description;

    private final String channelId;
    private final MessageTs ts;
    private final MessageTs threadTs;

    @Nullable private final MessageTs reply;
}
