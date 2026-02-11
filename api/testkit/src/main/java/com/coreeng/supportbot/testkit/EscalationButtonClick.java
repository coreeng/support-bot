package com.coreeng.supportbot.testkit;

import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.NonNull;

@Builder
@Getter
public class EscalationButtonClick implements MessageButtonClick {
    @NonNull private final String triggerId;

    @NonNull private final Ticket ticket;

    @Override
    public String actionId() {
        return TicketMessage.ESCALATE_BUTTON_ACTION_ID;
    }

    @Override
    public String privateMetadata() {
        return String.format("""
                {"ticketId": %d}
                """, ticket.id());
    }
}
