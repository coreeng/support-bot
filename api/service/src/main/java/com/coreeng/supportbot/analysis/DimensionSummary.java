package com.coreeng.supportbot.analysis;

import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.ticket.TicketId;

/** An aggregated analysis dimension with an example support request. */
public record DimensionSummary(
        String dimension, long queryCount, String summary, TicketId ticketId, MessageTs queryTs) {}
