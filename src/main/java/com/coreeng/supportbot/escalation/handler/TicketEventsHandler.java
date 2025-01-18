package com.coreeng.supportbot.escalation.handler;

import com.coreeng.supportbot.escalation.CreateEscalationRequest;
import com.coreeng.supportbot.escalation.EscalationProcessingService;
import com.coreeng.supportbot.ticket.TicketEscalated;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.coreeng.supportbot.ticket.TicketStatusChanged;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

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
        processingService.createEscalation(CreateEscalationRequest.builder()
            .ticket(event.ticket())
            .teamId(event.teamId())
            .threadPermalink(event.threadPermalink())
            .tags(event.tags())
            .build());
    }
}
