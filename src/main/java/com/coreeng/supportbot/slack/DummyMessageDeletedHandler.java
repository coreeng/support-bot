package com.coreeng.supportbot.slack;

import com.slack.api.app_backend.events.payload.EventsApiPayload;
import com.slack.api.bolt.context.builtin.EventContext;
import com.slack.api.methods.SlackApiException;
import com.slack.api.model.event.MessageDeletedEvent;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Looks like because we subscribed to message events, it automatically subscribed us to message deleted events too.
 * So we add a dummy handler to get rid of warnings that we are missing a handler for such events.
 */
@Component
public class DummyMessageDeletedHandler implements SlackEventHandler<MessageDeletedEvent> {
    @Override
    public Class<MessageDeletedEvent> getEventClass() {
        return MessageDeletedEvent.class;
    }

    @Override
    public void apply(EventsApiPayload<MessageDeletedEvent> event, EventContext context) throws IOException, SlackApiException {
        // noop
    }
}
