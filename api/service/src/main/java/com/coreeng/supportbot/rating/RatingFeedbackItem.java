package com.coreeng.supportbot.rating;

import com.coreeng.supportbot.ticket.TicketId;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * One piece of follow-up feedback as exposed over the API.
 *
 * <p>Deliberately carries only the week it was submitted in.
 *
 * @param ticketId present only when the submitter chose to share their name
 */
public record RatingFeedbackItem(
        int rating,
        LocalDate weekStart,
        String feedback,
        @Nullable TicketId ticketId) {}
