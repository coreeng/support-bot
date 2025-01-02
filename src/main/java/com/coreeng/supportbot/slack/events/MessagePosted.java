package com.coreeng.supportbot.slack.events;

import com.coreeng.supportbot.slack.MessageTs;
import com.slack.api.app_backend.events.payload.EventsApiPayload;
import com.slack.api.model.event.MessageEvent;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

public record MessagePosted(
    String message,
    String userId,
    String channelId,
    MessageTs messageTs,
    @Nullable MessageTs threadTs
) implements SlackEvent {
    public MessagePosted {
        checkNotNull(message);
        checkNotNull(userId);
        checkNotNull(channelId);
        checkNotNull(messageTs);
    }

    public static MessagePosted fromMessageEvent(EventsApiPayload<MessageEvent> event) {
        return new MessagePosted(
            event.getEvent().getText(),
            event.getEvent().getUser(),
            event.getEvent().getChannel(),
            MessageTs.of(event.getEvent().getTs()),
            null
        );
    }
}
