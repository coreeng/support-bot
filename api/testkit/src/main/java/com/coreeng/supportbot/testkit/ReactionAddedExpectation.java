package com.coreeng.supportbot.testkit;

import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.NonNull;

@Builder(toBuilder = true)
@Getter
public class ReactionAddedExpectation {
    @NonNull
    private final String description;
    private final String reaction;
    private final String channelId;
    private final MessageTs ts;
}
