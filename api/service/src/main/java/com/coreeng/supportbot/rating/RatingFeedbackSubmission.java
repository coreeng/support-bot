package com.coreeng.supportbot.rating;

import com.coreeng.supportbot.ticket.TicketId;
import java.util.UUID;

/**
 * Raw values submitted from the rating feedback modal.
 *
 * <p>{@code ticketId} is always known to the modal, but is only persisted when {@code shareName} is true.
 */
public record RatingFeedbackSubmission(UUID ratingId, TicketId ticketId, String feedback, boolean shareName) {}
