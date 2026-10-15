package com.coreeng.supportbot.summary;

import com.coreeng.supportbot.ticket.TicketId;
import com.google.common.collect.ImmutableSet;
import java.time.Instant;
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
 *   <li>The window's latest {@code ticket.updated_at}. The prose quotes the team and product
 *       breakdowns, which come from the {@code ticket} row and its tags rather than from
 *       {@code analysis}; a database trigger bumps the column when the row or its tags change (see
 *       migration V39), so correcting a ticket's team or product moves this and the cached prose is
 *       regenerated with the new attribution. Thread activity ({@code last_interacted_at}) does not
 *       bump it.
 *   <li>The window's classification gaps: closed tickets with no analysis row for the current prompt
 *       — the set the backfill targets. A newly closed ticket changes it and triggers a refresh. A
 *       ticket the backfill cannot classify (its Slack thread is gone, or the model keeps returning
 *       an unparseable answer) stays in it, but then it is part of the fingerprint the snapshot was
 *       stored under, so the summary is served rather than regenerated on every visit. The same
 *       applies to a ticket skipped on a transient LLM failure (rate limit, timeout): the backfill
 *       does not retry it within the run, so it is a gap like any other until the snapshot is older
 *       than {@code summary.failure-retry-delay}, when the next visit retries it (see {@code
 *       SummaryService}); a manual {@code /analysis/run} fills it sooner. That holds only for gaps
 *       the backfill actually attempted: the refresh stores its snapshot under
 *       {@link #withGapsAmong} the attempted ids, so a ticket that closed too late for the backfill
 *       to see is left out, the next visit's fingerprint differs, and it gets its own refresh.
 * </ul>
 *
 * <p>For a team group every one of those is limited to the group's tickets — a ticket closing in
 * another team must not regenerate every group's prose — and the group's own definition is digested
 * too ({@link SummaryScope#membershipDigest()}), so reconfiguring the group's members or label makes
 * its cached prose stale even when no ticket moves. The whole window has no such digest, so its
 * fingerprint reads exactly as it did before groups existed.
 *
 * @param ticketCount tickets raised in the window, whatever their status
 * @param analysisCount number of analysis rows for the window under the current prompt
 * @param maxUpdatedAt the latest {@code analysis.updated_at} among them, null when there are none
 * @param maxTicketUpdatedAt the latest {@code ticket.updated_at} among the window's tickets, null
 *     when the window has none
 * @param gapIds ids of the closed tickets in the window still awaiting classification
 * @param scopeDigest the team group's {@link SummaryScope#membershipDigest()}; null for the whole window
 */
public record SummaryFingerprint(
        long ticketCount,
        long analysisCount,
        @Nullable LocalDateTime maxUpdatedAt,
        @Nullable Instant maxTicketUpdatedAt,
        ImmutableSet<TicketId> gapIds,
        @Nullable String scopeDigest) {

    /** A fingerprint for the whole window. */
    public SummaryFingerprint(
            long ticketCount,
            long analysisCount,
            @Nullable LocalDateTime maxUpdatedAt,
            @Nullable Instant maxTicketUpdatedAt,
            ImmutableSet<TicketId> gapIds) {
        this(ticketCount, analysisCount, maxUpdatedAt, maxTicketUpdatedAt, gapIds, null);
    }

    /** A fingerprint for the whole window, with no classification gaps. */
    public SummaryFingerprint(
            long ticketCount,
            long analysisCount,
            @Nullable LocalDateTime maxUpdatedAt,
            @Nullable Instant maxTicketUpdatedAt) {
        this(ticketCount, analysisCount, maxUpdatedAt, maxTicketUpdatedAt, ImmutableSet.of());
    }

    /** Closed tickets in the window still awaiting classification. */
    public long gapCount() {
        return gapIds.size();
    }

    /** Sum of the gap ids, so a gap swapping for another of the same count still reads as a change. */
    public long gapIdSum() {
        return gapIds.stream().mapToLong(TicketId::id).sum();
    }

    /**
     * The same fingerprint with the gap component narrowed to the given ids — the tickets a backfill
     * attempted. A gap outside that set is one the backfill never tried, so it must not be baked into
     * the stored snapshot as if it had been given up on.
     */
    public SummaryFingerprint withGapsAmong(Set<TicketId> attempted) {
        ImmutableSet<TicketId> narrowed =
                gapIds.stream().filter(attempted::contains).collect(ImmutableSet.toImmutableSet());
        return new SummaryFingerprint(
                ticketCount, analysisCount, maxUpdatedAt, maxTicketUpdatedAt, narrowed, scopeDigest);
    }

    /**
     * Stable string form, stored in {@code summary_snapshot.fingerprint}: {@code
     * tickets/analysed@analysisUpdatedAt~ticketUpdatedAt}, with a {@code #gaps:idSum} suffix only when
     * there are gaps, so a fully classified window reads as {@code
     * tickets/analysed@analysisUpdatedAt~ticketUpdatedAt}, and a {@code |g:digest} suffix only for a
     * team group. Opaque to every consumer: nothing parses it, it is only compared for equality.
     */
    public String value() {
        String base = ticketCount + "/" + analysisCount + "@" + (maxUpdatedAt == null ? "-" : maxUpdatedAt.toString())
                + "~" + (maxTicketUpdatedAt == null ? "-" : maxTicketUpdatedAt.toString());
        String withGaps = gapIds.isEmpty() ? base : base + "#" + gapCount() + ":" + gapIdSum();
        return scopeDigest == null ? withGaps : withGaps + "|g:" + scopeDigest;
    }
}
