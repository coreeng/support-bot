package com.coreeng.supportbot.slack.events;

import com.coreeng.supportbot.slack.MessageTs;

import javax.annotation.Nullable;

public sealed interface SlackEvent
    permits ReactionAdded,
            ReactionRemoved,
            MessagePosted,
            BotTagged {

    String channelId();
    String userId();
    MessageTs messageTs();
    @Nullable
    MessageTs threadTs();
}
