package com.coreeng.supportbot.ticket.rest;

import com.coreeng.supportbot.ticket.TicketStatus;

import java.util.List;

public record TicketUpdateRequest(
    TicketStatus status,
    String authorsTeam,
    List<String> tags,
    String impact
) {
}

