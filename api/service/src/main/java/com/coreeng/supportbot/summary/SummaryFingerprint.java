package com.coreeng.supportbot.summary;

import com.google.common.collect.ImmutableSet;
import java.time.LocalDateTime;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Cheap digest of the data a window's summary was generated from.
 *
 * <p>Cache validity is decided by comparing this against the stored snapshot's value — there is no
 * timer. Four things are digested:
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
 *   <li>The window's ticket attribution: a hash over each ticket's team, status and tags. The prose
 *       quotes the team and product breakdowns, which come from the {@code ticket} row and its tags
 *       rather than from {@code analysis}, and the ticket table carries no {@code updated_at} — so
 *       without this, correcting a ticket's team or product would change the breakdowns on the page
 *       while the cached prose kept describing the old attribution.
 *   <li>The window's classification gaps: closed tickets with no analysis row for the current prompt
 *       — the set the backfill targets. A newly closed ticket changes it and triggers a refresh. A
 *       ticket the backfill cannot classify (its Slack thread is gone, or the model keeps returning
 *       an unparseable answer) stays in it, but then it is part of the fingerprint the snapshot was
 *       stored under, so the summary is served rather than regenerated on every visit. The same
 *       applies to a ticket skipped on a transient LLM failure (rate limit, timeout): the backfill
 *       does not retry it, so it is a gap like any other and is only revisited when the window's
 *       data changes — typically a manual {@code /analysis/run} filling it. That holds only for gaps
 *       the backfill actually attempted: the refresh stores its snapshot under
 *       {@link #withGapsAmong} the attempted ids, so a ticket that closed too late for the backfill
 *       to see is left out, the next visit's fingerprint differs, and it gets its own refresh.
 * </ul>
 *
 * @param ticketCount tickets raised in the window, whatever their status
 * @param analysisCount number of analysis rows for the window under the current prompt
 * @param maxUpdatedAt the latest {@code analysis.updated_at} among them, null when there are none
 * @param attribution hash of the window's per-ticket attribution (team, status, tag codes), null
 *     when the window has no tickets; opaque, only ever compared for equality
 * @param gapIds ids of the closed tickets in the window still awaiting classification
 */
public record SummaryFingerprint(
        long ticketCount,
        long analysisCount,
        @Nullable LocalDateTime maxUpdatedAt,
        @Nullable String attribution,
        ImmutableSet<Long> gapIds) {

    /** A fingerprint for a window with no classification gaps. */
    public SummaryFingerprint(
            long ticketCount, long analysisCount, @Nullable LocalDateTime maxUpdatedAt, @Nullable String attribution) {
        this(ticketCount, analysisCount, maxUpdatedAt, attribution, ImmutableSet.of());
    }

    /** Closed tickets in the window still awaiting classification. */
    public long gapCount() {
        return gapIds.size();
    }

    /** Sum of the gap ids, so a gap swapping for another of the same count still reads as a change. */
    public long gapIdSum() {
        return gapIds.stream().mapToLong(Long::longValue).sum();
    }

    /**
     * The same fingerprint with the gap component narrowed to the given ids — the tickets a backfill
     * attempted. A gap outside that set is one the backfill never tried, so it must not be baked into
     * the stored snapshot as if it had been given up on.
     */
    public SummaryFingerprint withGapsAmong(Set<Long> attempted) {
        ImmutableSet<Long> narrowed =
                gapIds.stream().filter(attempted::contains).collect(ImmutableSet.toImmutableSet());
        return new SummaryFingerprint(ticketCount, analysisCount, maxUpdatedAt, attribution, narrowed);
    }

    /**
     * Stable string form, stored in {@code summary_snapshot.fingerprint}: {@code
     * tickets/analysed@updatedAt~attribution}, with a {@code #gaps:idSum} suffix only when there are
     * gaps, so a fully classified window reads as {@code tickets/analysed@updatedAt~attribution}.
     * Opaque to every consumer: nothing parses it, it is only compared for equality.
     */
    public String value() {
        String base = ticketCount + "/" + analysisCount + "@" + (maxUpdatedAt == null ? "-" : maxUpdatedAt.toString())
                + "~" + (attribution == null ? "-" : attribution);
        return gapIds.isEmpty() ? base : base + "#" + gapCount() + ":" + gapIdSum();
    }
}
