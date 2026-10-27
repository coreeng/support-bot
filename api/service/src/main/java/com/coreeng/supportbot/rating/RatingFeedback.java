package com.coreeng.supportbot.rating;

import com.coreeng.supportbot.ticket.TicketId;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Follow-up feedback attached to an existing rating.
 *
 * @param ticketId only set when the submitter opted in to sharing their name; never set for anonymous feedback
 */
public record RatingFeedback(
        UUID ratingId, String feedback, @Nullable TicketId ticketId) {}
