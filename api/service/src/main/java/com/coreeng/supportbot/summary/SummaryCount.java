package com.coreeng.supportbot.summary;

import com.google.common.collect.ImmutableList;

/**
 * One row of a ranked breakdown: a dimension value and how many of the window's tickets carry it.
 *
 * @param label the dimension value, already bucketed — blanks are replaced with an explicit label
 *     rather than dropped, so a breakdown always reconciles against the window total
 * @param count number of tickets
 * @param recent the newest few tickets carrying this value, newest first — the examples shown when
 *     the row is expanded; empty for callers that only need the counts
 */
public record SummaryCount(String label, long count, ImmutableList<SummaryTicketExample> recent) {
    public SummaryCount(String label, long count) {
        this(label, count, ImmutableList.of());
    }
}
