package com.coreeng.supportbot.summary;

import java.time.LocalDate;

/**
 * One per-ticket {@code Reason} text ({@code analysis.summary}) together with the UTC day the ticket
 * was raised, so the model can read time-related patterns (a burst of the same complaint, a problem
 * that stopped after a fix) instead of a flat list of sentences.
 *
 * <p>The ticket's classification and team come with it, labelled exactly as in the breakdowns, so
 * when the model cites tickets for a claim about a breakdown value it can tell which tickets carry
 * that value instead of guessing from the reason text.
 *
 * @param ticketId the ticket the reason is for, which the model cites to back a claim (see {@link
 *     SummaryCitations})
 * @param raisedOn the ticket's {@code query.date} as a UTC calendar day — the same day the summary
 *     window is expressed in
 * @param driver the ticket's primary support driver, as its breakdown bucket labels it
 * @param category the ticket's category, as its breakdown bucket labels it
 * @param feature the ticket's platform feature, as its breakdown bucket labels it
 * @param team the ticket's tenant team, as its breakdown bucket labels it
 * @param text the reason as the classifier wrote it, never blank
 */
public record SummaryReason(
        long ticketId, LocalDate raisedOn, String driver, String category, String feature, String team, String text) {

    /**
     * The report line for this reason: {@code #<ticket> | YYYY-MM-DD | <driver> | <category> |
     * <feature> | <team> — <reason>}.
     */
    public String line() {
        return "#" + ticketId + " | " + raisedOn + " | " + driver + " | " + category + " | " + feature + " | " + team
                + " — " + text;
    }
}
