package com.coreeng.supportbot.summary;

import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.SlackId;
import com.coreeng.supportbot.ticket.TicketId;
import java.time.Instant;

/**
 * A ticket the summary cites for a claim, with what the page needs to show it and link to its
 * thread.
 *
 * @param ticketId the ticket, so the UI can open it
 * @param text the classifier's one-line {@code Reason} for the ticket ({@code analysis.summary}) —
 *     the line the model read when it cited the ticket
 * @param raisedAt when the ticket was raised ({@code query.date})
 * @param channel the Slack channel the thread is in
 * @param queryTs the Slack timestamp of the thread's opening message
 */
public record SummaryEvidence(
        TicketId ticketId, String text, Instant raisedAt, SlackId.Channel channel, MessageTs queryTs) {}
