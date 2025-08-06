package com.coreeng.supportbot.testkit;

import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.NonNull;

@Builder
@Getter
public class ReactionToAdd {
    @NonNull
    private final String userId;
    @NonNull
    private final String teamId;
    @NonNull
    private final String channelId;
    @NonNull
    private final String ts;
    @NonNull
    private final String reaction;
}
