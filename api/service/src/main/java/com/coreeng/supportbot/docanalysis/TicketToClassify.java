package com.coreeng.supportbot.docanalysis;

import com.coreeng.supportbot.ticket.TicketId;

/** A closed ticket and the Slack thread it was raised in. */
public record TicketToClassify(TicketId ticketId, String channelId, String threadTs) {}
