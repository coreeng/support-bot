package com.coreeng.supportbot.ticket;

import com.google.common.collect.ImmutableList;

public record TicketSubmission(
    TicketId ticketId,
    TicketStatus status,
    ImmutableList<String> tags,
    String impact
) {
}
