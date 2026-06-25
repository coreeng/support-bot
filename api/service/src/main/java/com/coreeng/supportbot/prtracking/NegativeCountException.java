package com.coreeng.supportbot.prtracking;

/** Thrown when any {@link RequestBreakdown} count is negative. */
public final class NegativeCountException extends RequestBreakdownInvariantException {

    public NegativeCountException(long totalSupportTickets, long totalPrTickets, long interventionPrTickets) {
        super("counts must not be negative: totalSupportTickets=%d, totalPrTickets=%d, interventionPrTickets=%d"
                .formatted(totalSupportTickets, totalPrTickets, interventionPrTickets));
    }
}
