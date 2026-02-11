package com.coreeng.supportbot.testkit;

import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Builder
@Getter
public class ReactionToAdd {
    @Nullable private final String userId;

    @Nullable private final String botId;

    @NonNull private final String teamId;

    @NonNull private final String channelId;

    @NonNull private final MessageTs ts;

    @NonNull private final String reaction;
}
