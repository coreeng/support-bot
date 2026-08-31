package com.coreeng.supportbot.summary;

import java.time.Instant;

/**
 * One ticket shown as an example under a breakdown row.
 *
 * @param ticketId the ticket, so the UI can open it
 * @param text the classifier's one-line {@code Reason} for the ticket ({@code analysis.summary});
 *     may be blank if the model returned none
 * @param raisedAt when the ticket was raised ({@code query.date})
 */
public record SummaryTicketExample(long ticketId, String text, Instant raisedAt) {}
