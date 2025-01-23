package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.enums.Tag;
import com.google.common.collect.ImmutableList;

import javax.annotation.Nullable;

public record TicketEscalated(
    Ticket ticket,
    String team,
    @Nullable
    String threadPermalink,
    ImmutableList<Tag> tags
) {
}
