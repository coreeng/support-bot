package com.coreeng.supportbot.summary;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * An inclusive range of days that a Support Summary covers.
 *
 * <p>The window is on ticket <em>creation</em> time ({@code query.date}) — "tickets raised between
 * {@code from} and {@code to}" — which is also what the backfill gap query uses, so the breakdowns
 * and the classification they depend on always describe the same set of tickets.
 */
public record SummaryWindow(LocalDate from, LocalDate to) {

    public SummaryWindow {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException(
                    "Summary window 'to' (" + to + ") must not be before 'from' (" + from + ")");
        }
    }

    /** Number of days covered, both ends included. */
    public long days() {
        return ChronoUnit.DAYS.between(from, to) + 1L;
    }
}
