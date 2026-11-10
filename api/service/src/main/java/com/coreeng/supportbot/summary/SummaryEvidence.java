package com.coreeng.supportbot.summary;

import java.time.Instant;

/**
 * A ticket the summary cites for a claim, with what the page needs to show it and link to its
 * thread.
 *
 * @param ticketId the ticket, so the UI can open it
 * @param text the classifier's one-line {@code Reason} for the ticket ({@code analysis.summary}) —
 *     the line the model read when it cited the ticket
 * @param raisedAt when the ticket was raised ({@code query.date})
 * @param channelId the Slack channel the thread is in
 * @param queryTs the Slack timestamp of the thread's opening message
 */
public record SummaryEvidence(long ticketId, String text, Instant raisedAt, String channelId, String queryTs) {}
