package com.coreeng.supportbot.rating;

import com.coreeng.supportbot.ticket.TicketId;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * One piece of follow-up feedback as exposed over the API.
 *
 * <p>Deliberately carries only the week it was submitted in, not the exact timestamp, so anonymous feedback
 * can't be correlated with ticket close times.
 *
 * @param ticketId present only when the submitter chose to share their name
 */
public record RatingFeedbackItem(
        int rating,
        LocalDate weekStart,
        String feedback,
        @Nullable TicketId ticketId) {}
