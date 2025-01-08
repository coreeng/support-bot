package com.coreeng.supportbot.slack.events;

import com.coreeng.supportbot.slack.MessageRef;

import static com.google.common.base.Preconditions.checkNotNull;

public record ReactionRemoved(
    String reaction,
    String userId,
    MessageRef messageRef
) implements SlackEvent {
    public ReactionRemoved {
        checkNotNull(reaction);
        checkNotNull(userId);
        checkNotNull(messageRef);
    }
}
