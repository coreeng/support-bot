package com.coreeng.supportbot.troubleshooting.router;

import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketQueryService;
import com.coreeng.supportbot.troubleshooting.ConditionalOnTroubleshootingEnabled;
import com.coreeng.supportbot.troubleshooting.TicketNotFoundForSessionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnTroubleshootingEnabled
@RequiredArgsConstructor
public class TicketContextLoader {
    private final TicketQueryService tickets;

    public TicketContext load(long ticketId) {
        Ticket ticket = tickets.findById(new TicketId(ticketId));
        if (ticket == null) {
            throw new TicketNotFoundForSessionException(ticketId);
        }
        return new TicketContext(
                ticketId,
                ticket.status().name(),
                ticket.team() == null ? null : ticket.team().toCode(),
                ticket.impact(),
                ticket.tags());
    }
}
