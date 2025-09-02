package com.coreeng.supportbot.testkit;

import lombok.Builder;
import lombok.Getter;

@Builder(toBuilder = true)
@Getter
public class ReactionAddedExpectation {
    private final String reaction;
    private final String channelId;
    private final MessageTs ts;
}
