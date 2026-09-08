package com.coreeng.supportbot.summary;

import java.time.LocalDate;

/**
 * One per-ticket {@code Reason} text ({@code analysis.summary}) together with the UTC day the ticket
 * was raised, so the model can read time-related patterns (a burst of the same complaint, a problem
 * that stopped after a fix) instead of a flat list of sentences.
 *
 * @param raisedOn the ticket's {@code query.date} as a UTC calendar day — the same day the summary
 *     window is expressed in
 * @param text the reason as the classifier wrote it, never blank
 */
public record SummaryReason(LocalDate raisedOn, String text) {

    /** The report line for this reason: {@code YYYY-MM-DD — <reason>}. */
    public String line() {
        return raisedOn + " — " + text;
    }
}
