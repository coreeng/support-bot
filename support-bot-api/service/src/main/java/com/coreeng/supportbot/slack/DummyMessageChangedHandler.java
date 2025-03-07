package com.coreeng.supportbot.slack;

import com.slack.api.app_backend.events.payload.EventsApiPayload;
import com.slack.api.bolt.context.builtin.EventContext;
import com.slack.api.methods.SlackApiException;
import com.slack.api.model.event.MessageChangedEvent;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Looks like because we subscribed to message events, it automatically subscribed us to message changed events too.
 * So we add a dummy handler to get rid of warnings that we are missing a handler for such events.
 */
@Component
public class DummyMessageChangedHandler implements SlackEventHandler<MessageChangedEvent> {
    @Override
    public Class<MessageChangedEvent> getEventClass() {
        return MessageChangedEvent.class;
    }

    @Override
    public void apply(EventsApiPayload<MessageChangedEvent> event, EventContext context) throws IOException, SlackApiException {
        // noop
    }
}
