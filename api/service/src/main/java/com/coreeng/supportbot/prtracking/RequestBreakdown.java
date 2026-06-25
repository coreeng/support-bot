package com.coreeng.supportbot.prtracking;

/**
 * Support-request funnel within a date range: how many support tickets came in, how many of those
 * had a PR, and how many of the PR tickets needed a manual engineer escalation.
 *
 * <p>All three counts share one date anchor — ticket creation (the {@code query} table's
 * {@code date} column) — so they are nested subsets: {@code interventionPrTickets <=
 * totalPrTickets <= totalSupportTickets}. Each invariant is enforced here and throws a dedicated
 * {@link RequestBreakdownInvariantException} so a query regression surfaces loudly (mapped to a
 * server error by the controller advice) rather than rendering a nonsensical &gt;100% rate in the UI.
 */
public record RequestBreakdown(long totalSupportTickets, long totalPrTickets, long interventionPrTickets) {
    public RequestBreakdown {
        if (totalSupportTickets < 0 || totalPrTickets < 0 || interventionPrTickets < 0) {
            throw new NegativeCountException(totalSupportTickets, totalPrTickets, interventionPrTickets);
        }
        if (totalPrTickets > totalSupportTickets) {
            throw new PrTicketsExceedSupportTicketsException(totalPrTickets, totalSupportTickets);
        }
        if (interventionPrTickets > totalPrTickets) {
            throw new InterventionExceedsPrTicketsException(interventionPrTickets, totalPrTickets);
        }
    }
}
