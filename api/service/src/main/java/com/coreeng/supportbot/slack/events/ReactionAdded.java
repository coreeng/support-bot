package com.coreeng.supportbot.slack.events;

import static com.google.common.base.Preconditions.checkNotNull;

import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.slack.MessageTs;
import com.slack.api.app_backend.events.payload.EventsApiPayload;
import com.slack.api.bolt.context.builtin.EventContext;
import com.slack.api.model.event.ReactionAddedEvent;

public record ReactionAdded(String reaction, String userId, MessageRef messageRef) implements SlackEvent {
    public ReactionAdded {
        checkNotNull(reaction);
        checkNotNull(userId);
        checkNotNull(messageRef);
    }

    public static ReactionAdded fromRaw(EventsApiPayload<ReactionAddedEvent> event, EventContext ctx) {
        return new ReactionAdded(
                event.getEvent().getReaction(),
                event.getEvent().getUser(),
                new MessageRef(
                        MessageTs.of(event.getEvent().getItem().getTs()),
                        MessageTs.ofOrNull(ctx.getThreadTs()),
                        event.getEvent().getItem().getChannel()));
    }
}
