package com.coreeng.supportbot.testkit;

import org.jspecify.annotations.NonNull;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class EscalationButtonClick implements MessageButtonClick {
    @NonNull
    private final String triggerId;
    @NonNull
    private final Ticket ticket;

    @Override
    public void preSetupMocks() {
    }

    @Override
    public String actionId() {
        return TicketMessage.escalateButtonActionId;
    }

    @Override
    public String privateMetadata() {
        return String.format("""
                {"ticketId": %d}
                """, ticket.id());
    }
}


