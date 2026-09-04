package com.coreeng.supportbot.summary;

import com.google.common.collect.ImmutableList;
import org.jspecify.annotations.Nullable;

/**
 * One row of a ranked breakdown: a dimension value and how many of the window's tickets carry it.
 *
 * @param label the dimension value, already bucketed — blanks are replaced with an explicit label
 *     rather than dropped, so a breakdown always reconciles against the window total
 * @param count number of tickets
 * @param recent the newest few tickets carrying this value, newest first — the examples shown when
 *     the row is expanded; empty for callers that only need the counts
 * @param topProduct for the teams breakdown, the product (by product tag) this team's tickets most
 *     often carry in the window, ties broken alphabetically; null when none of them carries one, and
 *     always null for the other breakdowns
 */
public record SummaryCount(
        String label,
        long count,
        ImmutableList<SummaryTicketExample> recent,
        @Nullable String topProduct) {
    public SummaryCount(String label, long count) {
        this(label, count, ImmutableList.of(), null);
    }

    public SummaryCount(String label, long count, ImmutableList<SummaryTicketExample> recent) {
        this(label, count, recent, null);
    }
}
