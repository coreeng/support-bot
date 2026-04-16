package com.coreeng.supportbot.rating;

import com.coreeng.supportbot.ticket.TicketId;

public class RatingTicketNotFoundException extends RuntimeException {
    public RatingTicketNotFoundException(TicketId ticketId) {
        super("Ticket not found: " + ticketId.render());
    }
}
