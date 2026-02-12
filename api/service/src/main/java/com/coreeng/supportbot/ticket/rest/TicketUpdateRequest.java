package com.coreeng.supportbot.ticket.rest;

import com.coreeng.supportbot.ticket.TicketStatus;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record TicketUpdateRequest(
        @Nullable TicketStatus status,
        @Nullable String authorsTeam,
        @Nullable List<String> tags,
        @Nullable String impact,
        @Nullable String assignedTo) {}
