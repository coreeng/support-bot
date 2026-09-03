package com.coreeng.supportbot.summary;

import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * Cheap digest of the data a window's summary was generated from.
 *
 * <p>Cache validity is decided by comparing this against the stored snapshot's value — there is no
 * timer. Three things are digested:
 * <ul>
 *   <li>The window's ticket count: every ticket raised in the window in a monitored channel, open or
 *       closed. The prose quotes the window total and the "still open or not yet classified" figure,
 *       so a ticket raised after the snapshot (a viewer whose window ends today) makes it stale even
 *       though it is not yet a classification gap.
 *   <li>The window's {@code analysis} rows under the current prompt. Rows are only ever inserted or
 *       updated (never deleted), so a change to either the row count or the latest
 *       {@code updated_at} means the classifications moved and the prose is stale. This also catches
 *       cross-window drift: a backfill triggered from one window can classify tickets that fall in
 *       another cached window, and that window's fingerprint changes with it.
 *   <li>The window's classification gaps: closed tickets with no analysis row for the current prompt
 *       — the set the backfill targets. A newly closed ticket changes it and triggers a refresh. A
 *       ticket the backfill cannot classify (its Slack thread is gone, or the model keeps returning
 *       an unparseable answer) stays in it, but then it is part of the fingerprint the snapshot was
 *       stored under, so the summary is served rather than regenerated on every visit. The same
 *       applies to a ticket skipped on a transient LLM failure (rate limit, timeout): the backfill
 *       does not retry it, so it is a gap like any other and is only revisited when the window's
 *       data changes — typically a manual {@code /analysis/run} filling it.
 * </ul>
 *
 * @param ticketCount tickets raised in the window, whatever their status
 * @param analysisCount number of analysis rows for the window under the current prompt
 * @param maxUpdatedAt the latest {@code analysis.updated_at} among them, null when there are none
 * @param gapCount closed tickets in the window still awaiting classification
 * @param gapIdSum sum of those tickets' ids, so a gap swapping for another of the same count still
 *     reads as a change
 */
public record SummaryFingerprint(
        long ticketCount, long analysisCount, @Nullable LocalDateTime maxUpdatedAt, long gapCount, long gapIdSum) {

    /** A fingerprint for a window with no classification gaps. */
    public SummaryFingerprint(long ticketCount, long analysisCount, @Nullable LocalDateTime maxUpdatedAt) {
        this(ticketCount, analysisCount, maxUpdatedAt, 0L, 0L);
    }

    /**
     * Stable string form, stored in {@code summary_snapshot.fingerprint}: {@code
     * tickets/analysed@updatedAt}, with a gap suffix only when there are gaps, so a fully classified
     * window reads as {@code tickets/analysed@updatedAt}.
     */
    public String value() {
        String base = ticketCount + "/" + analysisCount + "@" + (maxUpdatedAt == null ? "-" : maxUpdatedAt.toString());
        return gapCount == 0 ? base : base + "#" + gapCount + ":" + gapIdSum;
    }
}
