package com.coreeng.supportbot.summary;

import com.coreeng.supportbot.ticket.TicketId;
import com.google.common.collect.ImmutableMap;
import java.util.Collection;

/**
 * Windowed read side for the Support Summary page.
 *
 * <p>Everything here is scoped to tickets raised inside the window in a monitored channel — and,
 * for a team group, attributed to one of its teams — and, where analysis is involved, to rows
 * carrying the current prompt ID, so the figures never mix classifications produced by different
 * prompt versions.
 */
public interface SummaryReadRepository {

    /** The live ranked breakdowns for the window. */
    SummaryBreakdowns breakdowns(SummaryScope scope, String promptId, Collection<String> channelIds);

    /** Digest of the window's analysis rows, used to decide whether a cached summary is still valid. */
    SummaryFingerprint fingerprint(SummaryScope scope, String promptId, Collection<String> channelIds);

    /**
     * The per-ticket {@code Reason} texts ({@code analysis.summary}) for the window, each with the
     * day its ticket was raised — the raw material the LLM summarises alongside the aggregated
     * counts.
     *
     * @param limit maximum number of reasons to return; keeps a very wide window from overflowing
     *     the model's context. When there are more, the ones returned are spread evenly across the
     *     window, starting with the newest, rather than the newest alone
     * @return the reasons, newest ticket first, with how many the scope had before sampling
     */
    SummaryReasons reasons(SummaryScope scope, String promptId, Collection<String> channelIds, int limit);

    /**
     * The tickets a summary cites, looked up under the same scope as everything else here, so a
     * ticket that has left the scope (or lost its analysis for the prompt) is simply absent.
     *
     * @return the found tickets by id; ids that do not resolve have no entry
     */
    ImmutableMap<TicketId, SummaryEvidence> citedTickets(
            SummaryScope scope, String promptId, Collection<String> channelIds, Collection<TicketId> ticketIds);
}
