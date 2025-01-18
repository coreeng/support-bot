package com.coreeng.supportbot.ticket;

public sealed interface ToggleResult permits ToggleResult.Success, ToggleResult.RequiresConfirmation {
    record Success() implements ToggleResult {}

    record RequiresConfirmation(
        TicketId ticketId,
        long unresolvedEscalations
    ) implements ToggleResult {
    }
}
