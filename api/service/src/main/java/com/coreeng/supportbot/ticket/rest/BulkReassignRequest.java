package com.coreeng.supportbot.ticket.rest;

import com.coreeng.supportbot.ticket.TicketId;
import java.util.List;

public record BulkReassignRequest(List<TicketId> ticketIds, String assignedTo) {}
