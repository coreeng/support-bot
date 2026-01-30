package com.coreeng.supportbot.testkit;

import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Builder
@Getter
public class MessageToPost {
    @Nullable
    private final String userId;
    @Nullable
    private final String botId;

    @NonNull
    private final String teamId;
    @NonNull
    private final String channelId;
    @NonNull
    private final String message;
    @NonNull
    private final MessageTs ts;
    /**
     * Thread timestamp - set when posting a reply within a thread.
     * When set, the message is posted as a reply to the thread.
     */
    @Nullable
    private final MessageTs threadTs;
}
