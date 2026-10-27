package com.coreeng.supportbot.rating;

import com.coreeng.supportbot.ticket.TicketId;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A rating that has follow-up feedback attached, as read back from storage. */
public record RatingFeedbackEntry(
        UUID ratingId,
        int rating,
        String submittedTs,
        String feedback,
        @Nullable TicketId ticketId) {}
