package com.coreeng.supportbot.slack.events;

import com.coreeng.supportbot.slack.MessageRef;

public sealed interface SlackEvent
    permits ReactionAdded,
    ReactionRemoved,
    MessagePosted,
    BotTagged {

    String userId();

    MessageRef messageRef();
}
