package com.coreeng.supportbot.ticket;

import com.google.common.collect.ImmutableList;
import org.jspecify.annotations.Nullable;

public record TicketEscalated(
        Ticket ticket, String team, @Nullable String threadPermalink, ImmutableList<String> tags) {}
