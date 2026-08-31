package com.coreeng.supportbot.summary;

import com.google.common.collect.ImmutableList;

/**
 * The live aggregations for one window. Computed from SQL on every request — unlike the prose
 * summary, they are cheap and never cached.
 *
 * <p>Two totals matter and they are deliberately different:
 * <ul>
 *   <li>{@code totalTickets} — every ticket raised in the window in a monitored channel.
 *   <li>{@code classifiedTickets} — those with an {@code analysis} row for the <em>current</em>
 *       prompt. {@code drivers}, {@code categories} and {@code features} only exist for these, so
 *       each of those breakdowns sums to {@code classifiedTickets}, and
 *       {@code unclassifiedTickets()} is the explicit remainder (still open, or awaiting backfill).
 * </ul>
 *
 * <p>{@code teams} comes from {@code ticket.team}, which is known for every ticket, so it sums to
 * {@code totalTickets}.
 *
 * <p>Every row of every breakdown carries its newest few example tickets ({@link SummaryCount#recent}),
 * including the explicit blank buckets.
 */
public record SummaryBreakdowns(
        SummaryWindow window,
        long totalTickets,
        long classifiedTickets,
        ImmutableList<SummaryCount> drivers,
        ImmutableList<SummaryCount> categories,
        ImmutableList<SummaryCount> features,
        ImmutableList<SummaryCount> teams) {

    /** Tickets in the window with no analysis for the current prompt: still open, or not yet backfilled. */
    public long unclassifiedTickets() {
        return Math.max(0L, totalTickets - classifiedTickets);
    }
}
