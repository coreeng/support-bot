package com.coreeng.supportbot.ticket.handler;

import com.coreeng.supportbot.escalation.EscalationCreated;
import com.coreeng.supportbot.ticket.TicketProcessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EscalationEventHandler {
    private final TicketProcessingService processingService;

    @EventListener
    public void onEscalationCreated(EscalationCreated event) {
        processingService.postTicketEscalatedMessage(event.escalationId());
    }
}
