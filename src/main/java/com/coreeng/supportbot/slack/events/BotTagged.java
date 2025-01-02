package com.coreeng.supportbot.slack.events;

import com.coreeng.supportbot.slack.MessageTs;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

public record BotTagged(
    String reaction,
    String userId,
    String channelId,
    MessageTs messageTs,
    @Nullable MessageTs threadTs
) implements SlackEvent {
    public BotTagged {
        checkNotNull(reaction);
        checkNotNull(userId);
        checkNotNull(channelId);
        checkNotNull(messageTs);
    }
}
