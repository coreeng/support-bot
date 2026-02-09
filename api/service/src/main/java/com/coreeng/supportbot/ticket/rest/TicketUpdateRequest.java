package com.coreeng.supportbot.ticket.rest;

import com.coreeng.supportbot.ticket.TicketStatus;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record TicketUpdateRequest(
    @Nullable
    TicketStatus status,
    @Nullable
    String authorsTeam,
    @Nullable
    List<String> tags,
    @Nullable
    String impact,
    @Nullable
    String assignedTo
) {
}
