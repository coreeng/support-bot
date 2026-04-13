package com.coreeng.supportbot.ticket.handler;

import com.coreeng.supportbot.slack.SlackEventHandler;
import com.coreeng.supportbot.slack.events.MessagePosted;
import com.coreeng.supportbot.ticket.TicketProcessingService;
import com.slack.api.app_backend.events.payload.EventsApiPayload;
import com.slack.api.bolt.context.builtin.EventContext;
import com.slack.api.model.event.MessageBotEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BotMessagePostedHandler implements SlackEventHandler<MessageBotEvent> {
    private final TicketProcessingService ticketProcessingService;

    @Override
    public Class<MessageBotEvent> getEventClass() {
        return MessageBotEvent.class;
    }

    @Override
    public void apply(EventsApiPayload<MessageBotEvent> event, EventContext context) {
        ticketProcessingService.handleMessagePosted(MessagePosted.fromBotMessageEvent(event));
    }
}
