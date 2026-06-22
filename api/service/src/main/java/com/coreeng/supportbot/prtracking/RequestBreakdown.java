package com.coreeng.supportbot.prtracking;

/**
 * Support-request funnel within a date range: how many support tickets came in, how many of those
 * had a PR, and how many of the PR tickets needed a manual engineer escalation.
 *
 * <p>All three counts share one date anchor — ticket creation (the {@code query} table's
 * {@code date} column) — so they are nested subsets: {@code interventionPrTickets <=
 * totalPrTickets <= totalSupportTickets}. The
 * invariant is enforced here so a query regression surfaces loudly rather than rendering a
 * nonsensical >100% rate in the UI.
 */
public record RequestBreakdown(long totalSupportTickets, long totalPrTickets, long interventionPrTickets) {
    public RequestBreakdown {
        if (totalSupportTickets < 0 || totalPrTickets < 0 || interventionPrTickets < 0) {
            throw new IllegalArgumentException("counts must not be negative");
        }
        if (totalPrTickets > totalSupportTickets) {
            throw new IllegalArgumentException("totalPrTickets must not exceed totalSupportTickets");
        }
        if (interventionPrTickets > totalPrTickets) {
            throw new IllegalArgumentException("interventionPrTickets must not exceed totalPrTickets");
        }
    }
}
