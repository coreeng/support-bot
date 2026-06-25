package com.coreeng.supportbot.prtracking;

/** Thrown when a {@link RequestBreakdown} reports more PR tickets than total support tickets. */
public final class PrTicketsExceedSupportTicketsException extends RequestBreakdownInvariantException {

    public PrTicketsExceedSupportTicketsException(long totalPrTickets, long totalSupportTickets) {
        super("totalPrTickets must not exceed totalSupportTickets: totalPrTickets=%d, totalSupportTickets=%d"
                .formatted(totalPrTickets, totalSupportTickets));
    }
}
