package com.coreeng.supportbot.ticket.handler;

import org.springframework.stereotype.Component;

import com.coreeng.supportbot.slack.SlackEventHandler;
import com.coreeng.supportbot.slack.SlackId;
import com.coreeng.supportbot.rbac.RbacService;
import com.coreeng.supportbot.slack.events.ReactionAdded;
import com.coreeng.supportbot.ticket.TicketProcessingService;
import com.slack.api.app_backend.events.payload.EventsApiPayload;
import com.slack.api.bolt.context.builtin.EventContext;
import com.slack.api.model.event.ReactionAddedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReactionAddedHandler implements SlackEventHandler<ReactionAddedEvent> {
    private final TicketProcessingService ticketProcessingService;
    private final RbacService rbacService;

    @Override
    public Class<ReactionAddedEvent> getEventClass() {
        return ReactionAddedEvent.class;
    }

    @Override
    public void apply(EventsApiPayload<ReactionAddedEvent> event, EventContext context) {
        if (!rbacService.isSupportBySlackId(SlackId.user(event.getEvent().getUser()))) {
            log.atInfo()
                .addArgument(() -> event.getEvent().getReaction())
                .addArgument(() -> event.getEvent().getUser())
                .log("Skipping reaction added({}). User({}) is not a support team member");
            return;
        }
        ticketProcessingService.handleReactionAdded(ReactionAdded.fromRaw(event, context));
    }
}
