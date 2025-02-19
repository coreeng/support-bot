package com.coreeng.supportbot.ticket;

import java.time.Instant;

public record TicketCreatedMessage(
    TicketId ticketId,
    TicketStatus status,
    Instant statusChangedDate
) {
}
