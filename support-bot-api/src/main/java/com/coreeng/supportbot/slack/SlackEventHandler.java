package com.coreeng.supportbot.slack;

import com.slack.api.app_backend.events.payload.EventsApiPayload;
import com.slack.api.bolt.context.builtin.EventContext;
import com.slack.api.methods.SlackApiException;
import com.slack.api.model.event.Event;

import java.io.IOException;

public interface SlackEventHandler<E extends Event> {
    Class<E> getEventClass();
    void apply(EventsApiPayload<E> event, EventContext context) throws IOException, SlackApiException;

    @SuppressWarnings("unchecked")
    default void applyUntyped(EventsApiPayload<? extends Event> event, EventContext context) throws IOException, SlackApiException {
        apply((EventsApiPayload<E>) event, context);
    }
}
