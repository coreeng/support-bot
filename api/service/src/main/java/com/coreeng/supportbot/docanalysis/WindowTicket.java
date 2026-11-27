package com.coreeng.supportbot.docanalysis;

import com.coreeng.supportbot.ticket.TicketId;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * A closed ticket of a window and its row in {@code doc_analysis}.
 *
 * @param threadTs the Slack timestamp of the thread's opening message
 * @param analysis null when the ticket has no row
 */
public record WindowTicket(
        TicketId ticketId,
        String channelId,
        String threadTs,
        Instant raisedAt,
        @Nullable DocAnalysis analysis) {}
