package com.coreeng.supportbot.summary;

import com.google.common.collect.ImmutableList;
import java.util.Collection;

/**
 * Windowed read side for the Support Summary page.
 *
 * <p>Everything here is scoped to tickets raised inside the window in a monitored channel, and —
 * where analysis is involved — to rows carrying the current prompt ID, so the figures never mix
 * classifications produced by different prompt versions.
 */
public interface SummaryReadRepository {

    /** The live ranked breakdowns for the window. */
    SummaryBreakdowns breakdowns(SummaryWindow window, String promptId, Collection<String> channelIds);

    /** Digest of the window's analysis rows, used to decide whether a cached summary is still valid. */
    SummaryFingerprint fingerprint(SummaryWindow window, String promptId, Collection<String> channelIds);

    /**
     * The per-ticket {@code Reason} texts ({@code analysis.summary}) for the window — the raw
     * material the LLM summarises alongside the aggregated counts.
     *
     * @param limit maximum number of reasons to return, newest ticket first; keeps a very wide
     *     window from overflowing the model's context
     */
    ImmutableList<String> reasons(SummaryWindow window, String promptId, Collection<String> channelIds, int limit);
}
