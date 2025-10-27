package com.coreeng.supportbot.rating;

import com.coreeng.supportbot.ticket.TicketId;

public record RatingButtonInput(
    TicketId ticketId,
    int rating
) {
}
