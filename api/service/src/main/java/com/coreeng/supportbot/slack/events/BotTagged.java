package com.coreeng.supportbot.slack.events;

import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.slack.MessageTs;
import com.slack.api.app_backend.events.payload.EventsApiPayload;
import com.slack.api.model.event.AppMentionEvent;

import static com.google.common.base.Preconditions.checkNotNull;

public record BotTagged(
    String message,
    String userId,
    MessageRef messageRef
) implements SlackEvent {
    public BotTagged {
        checkNotNull(message);
        checkNotNull(userId);
        checkNotNull(messageRef);
    }

    public static BotTagged fromRaw(EventsApiPayload<AppMentionEvent> event) {
        return new BotTagged(
            event.getEvent().getText(),
            event.getEvent().getUser(),
            new MessageRef(
                MessageTs.of(event.getEvent().getTs()),
                MessageTs.ofOrNull(event.getEvent().getThreadTs()),
                event.getEvent().getChannel()
            )
        );
    }
}
