package com.coreeng.supportbot.ticket.handler;

import com.coreeng.supportbot.slack.SlackEventHandler;
import com.coreeng.supportbot.slack.events.MessageDeleted;
import com.coreeng.supportbot.ticket.TicketProcessingService;
import com.slack.api.app_backend.events.payload.EventsApiPayload;
import com.slack.api.bolt.context.builtin.EventContext;
import com.slack.api.model.event.MessageDeletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MessageDeletedHandler implements SlackEventHandler<MessageDeletedEvent> {
    private final TicketProcessingService ticketProcessingService;

    @Override
    public Class<MessageDeletedEvent> getEventClass() {
        return MessageDeletedEvent.class;
    }

    @Override
    public void apply(EventsApiPayload<MessageDeletedEvent> event, EventContext context) {
        ticketProcessingService.handleMessageDeleted(MessageDeleted.fromMessageDeletedEvent(event));
    }
}
