package com.coreeng.supportbot.rating;

import com.coreeng.supportbot.ticket.TicketId;
import java.util.UUID;

/** Context carried through the feedback modal's private metadata. */
public record RatingFeedbackViewInput(UUID ratingId, TicketId ticketId, int rating) {
    public boolean isPositive() {
        return rating >= 4;
    }
}
