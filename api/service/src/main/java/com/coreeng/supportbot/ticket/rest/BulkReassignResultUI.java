package com.coreeng.supportbot.ticket.rest;

import com.coreeng.supportbot.ticket.TicketId;
import com.google.common.collect.ImmutableList;

public record BulkReassignResultUI(
        int successCount,
        ImmutableList<TicketId> successfulTicketIds,
        int skippedCount,
        ImmutableList<TicketId> skippedTicketIds,
        String message) {}
