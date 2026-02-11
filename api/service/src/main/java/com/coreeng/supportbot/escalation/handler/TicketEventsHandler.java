package com.coreeng.supportbot.escalation.handler;

import com.coreeng.supportbot.escalation.CreateEscalationRequest;
import com.coreeng.supportbot.escalation.EscalationProcessingService;
import com.coreeng.supportbot.ticket.TicketEscalated;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.coreeng.supportbot.ticket.TicketStatusChanged;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TicketEventsHandler {
    private final EscalationProcessingService processingService;

    @EventListener
    public void onTicketStatusChange(TicketStatusChanged event) {
        if (event.status() == TicketStatus.closed) {
            processingService.resolveByTicketId(event.ticketId());
        }
    }

    @EventListener
    public void onTicketEscalated(TicketEscalated event) {
        try {
            processingService.createEscalation(CreateEscalationRequest.builder()
                    .ticket(event.ticket())
                    .team(event.team())
                    .tags(event.tags())
                    .build());
        } catch (Exception e) {
            log.atError()
                    .addArgument(() -> event.ticket().id())
                    .setCause(e)
                    .log("Couldn't create escalation for ticket({})");
        }
    }
}
