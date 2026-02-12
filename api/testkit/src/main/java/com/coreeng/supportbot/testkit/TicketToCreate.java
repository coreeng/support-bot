package com.coreeng.supportbot.testkit;

import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.NonNull;

@Builder
@Getter
public class TicketToCreate {
    @NonNull @Builder.Default
    private final String opDescription = "create ticket";

    @NonNull private final String channelId;

    @NonNull private final MessageTs queryTs;

    @NonNull private final MessageTs createdMessageTs;

    @Builder.Default
    private final String message = "I have a problem";
}
