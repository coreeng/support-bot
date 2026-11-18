package com.coreeng.supportbot.troubleshooting.router;

import com.google.common.collect.ImmutableList;
import org.jspecify.annotations.Nullable;

/** The ticket fields the router is told about. */
public record TicketContext(
        long ticketId,
        String status,
        @Nullable String team,
        @Nullable String impact,
        ImmutableList<String> tags) {}
