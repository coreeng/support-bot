package com.coreeng.supportbot.ticket;

public sealed interface TicketSubmitResult permits
    TicketSubmitResult.Success,
    TicketSubmitResult.RequiresConfirmation {

    record Success() implements TicketSubmitResult {
    }

    record RequiresConfirmation(
        TicketSubmission submission,
        ConfirmationCause cause
    ) implements TicketSubmitResult {
    }

    record ConfirmationCause(
        long unresolvedEscalations
    ) {}
}
