package com.coreeng.supportbot.slack.events;

import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.slack.MessageTs;
import com.slack.api.app_backend.events.payload.EventsApiPayload;
import com.slack.api.model.event.MessageEvent;

import static com.google.common.base.Preconditions.checkNotNull;

public record MessagePosted(
    String message,
    String userId,
    MessageRef messageRef
) implements SlackEvent {
    public MessagePosted {
        checkNotNull(message);
        checkNotNull(userId);
        checkNotNull(messageRef);
    }

    public static MessagePosted fromMessageEvent(EventsApiPayload<MessageEvent> event) {
        return new MessagePosted(
            event.getEvent().getText(),
            event.getEvent().getUser(),
            new MessageRef(
                MessageTs.of(event.getEvent().getTs()),
                null,
                event.getEvent().getChannel()
            )
        );
    }
}
