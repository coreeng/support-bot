package com.coreeng.supportbot.slack.events;


import com.coreeng.supportbot.slack.MessageTs;
import com.slack.api.app_backend.events.payload.EventsApiPayload;
import com.slack.api.bolt.context.builtin.EventContext;
import com.slack.api.model.event.ReactionAddedEvent;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

public record ReactionAdded(
    String reaction,
    String userId,
    String channelId,
    MessageTs messageTs,
    @Nullable MessageTs threadTs
) implements SlackEvent {
    public ReactionAdded {
        checkNotNull(reaction);
        checkNotNull(userId);
        checkNotNull(channelId);
        checkNotNull(messageTs);
    }

    public static ReactionAdded fromRaw(EventsApiPayload<ReactionAddedEvent> event, EventContext ctx) {
        return new ReactionAdded(
            event.getEvent().getReaction(),
            event.getEvent().getUser(),
            event.getEvent().getItem().getChannel(),
            MessageTs.of(event.getEvent().getItem().getTs()),
            MessageTs.ofOrNull(ctx.getThreadTs())
        );
    }
}
