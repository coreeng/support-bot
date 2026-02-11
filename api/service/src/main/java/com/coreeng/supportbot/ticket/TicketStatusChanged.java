package com.coreeng.supportbot.ticket;

public record TicketStatusChanged(TicketId ticketId, TicketStatus status) {}
