package com.coreeng.supportbot.testkit;

import org.jspecify.annotations.NonNull;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class TicketToCreate {
    @NonNull
    private final String channelId;
    @NonNull
    private final MessageTs queryTs;
    @NonNull
    private final MessageTs createdMessageTs;

    @Builder.Default
    private final String message = "I have a problem";
}
