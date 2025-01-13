package com.coreeng.supportbot.ticket.handler;

import com.coreeng.supportbot.slack.SlackEventHandler;
import com.coreeng.supportbot.slack.events.ReactionAdded;
import com.coreeng.supportbot.ticket.TicketProcessingService;
import com.slack.api.app_backend.events.payload.EventsApiPayload;
import com.slack.api.bolt.context.builtin.EventContext;
import com.slack.api.model.event.ReactionAddedEvent;
import org.springframework.stereotype.Component;

@Component
public class ReactionAddedHandler implements SlackEventHandler<ReactionAddedEvent> {
    private final TicketProcessingService ticketProcessingService;

    public ReactionAddedHandler(TicketProcessingService ticketProcessingService) {
        this.ticketProcessingService = ticketProcessingService;
    }

    @Override
    public Class<ReactionAddedEvent> getEventClass() {
        return ReactionAddedEvent.class;
    }

    @Override
    public void apply(EventsApiPayload<ReactionAddedEvent> event, EventContext context) {
        ticketProcessingService.handleReactionAdded(ReactionAdded.fromRaw(event, context));
    }
}
