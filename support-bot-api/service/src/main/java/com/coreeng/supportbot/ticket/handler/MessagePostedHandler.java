package com.coreeng.supportbot.ticket.handler;

import com.coreeng.supportbot.slack.SlackEventHandler;
import com.coreeng.supportbot.slack.events.MessagePosted;
import com.coreeng.supportbot.ticket.TicketProcessingService;
import com.slack.api.app_backend.events.payload.EventsApiPayload;
import com.slack.api.bolt.context.builtin.EventContext;
import com.slack.api.model.event.MessageEvent;
import org.springframework.stereotype.Component;

@Component
public class MessagePostedHandler implements SlackEventHandler<MessageEvent> {
    private final TicketProcessingService ticketProcessingService;

    public MessagePostedHandler(TicketProcessingService ticketProcessingService) {
        this.ticketProcessingService = ticketProcessingService;
    }

    @Override
    public Class<MessageEvent> getEventClass() {
        return MessageEvent.class;
    }

    @Override
    public void apply(EventsApiPayload<MessageEvent> event, EventContext context) {
        ticketProcessingService.handleMessagePosted(MessagePosted.fromMessageEvent(event));
    }
}
