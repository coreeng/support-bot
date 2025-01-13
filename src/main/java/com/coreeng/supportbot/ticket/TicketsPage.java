package com.coreeng.supportbot.ticket;

import com.google.common.collect.ImmutableList;

public record TicketsPage(
    ImmutableList<Ticket> tickets,
    int page,
    int totalPages,
    int totalTickets
) {
}
