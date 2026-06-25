package com.coreeng.supportbot.prtracking;

/** Thrown when a {@link RequestBreakdown} reports more intervention tickets than PR tickets. */
public final class InterventionExceedsPrTicketsException extends RequestBreakdownInvariantException {

    public InterventionExceedsPrTicketsException(long interventionPrTickets, long totalPrTickets) {
        super("interventionPrTickets must not exceed totalPrTickets: interventionPrTickets=%d, totalPrTickets=%d"
                .formatted(interventionPrTickets, totalPrTickets));
    }
}
