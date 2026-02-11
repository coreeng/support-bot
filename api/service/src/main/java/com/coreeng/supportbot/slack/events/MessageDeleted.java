package com.coreeng.supportbot.slack.events;

import static com.google.common.base.Preconditions.checkNotNull;

import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.slack.MessageTs;
import com.slack.api.app_backend.events.payload.EventsApiPayload;
import com.slack.api.model.event.MessageDeletedEvent;

public record MessageDeleted(MessageRef messageRef) {
    public MessageDeleted {
        checkNotNull(messageRef);
    }

    public static MessageDeleted fromMessageDeletedEvent(EventsApiPayload<MessageDeletedEvent> event) {
        var e = event.getEvent();
        return new MessageDeleted(new MessageRef(
                MessageTs.of(e.getDeletedTs()),
                MessageTs.ofOrNull(
                        e.getPreviousMessage() != null ? e.getPreviousMessage().getThreadTs() : null),
                e.getChannel()));
    }
}
