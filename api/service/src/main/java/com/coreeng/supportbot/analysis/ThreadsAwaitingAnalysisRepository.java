package com.coreeng.supportbot.analysis;

import com.google.common.collect.ImmutableList;
import java.time.LocalDate;
import java.util.Collection;

/**
 * Repository for finding Slack threads that need LLM analysis.
 *
 * <p>This repository identifies threads from closed tickets that either:
 * <ul>
 *   <li>Have never been analyzed, or</li>
 *   <li>Were analyzed with a different prompt ID (prompt has changed)</li>
 * </ul>
 */
public interface ThreadsAwaitingAnalysisRepository {

    /**
     * Finds threads from closed tickets last interacted with in the last N days that don't have an
     * analysis record with the given prompt ID.
     *
     * @param days Number of days to look back from today
     * @param promptId The current prompt ID to check against existing analysis records
     * @param channelIds Slack channel IDs to include; tickets from any of these channels are returned
     * @return Immutable list of threads that need analysis
     */
    ImmutableList<ThreadToAnalyze> findThreadsAwaitingAnalysis(
            int days, String promptId, Collection<String> channelIds);

    /**
     * Finds threads from closed tickets <em>raised</em> in the given inclusive day range that don't
     * have an analysis record with the given prompt ID.
     *
     * <p>Unlike {@link #findThreadsAwaitingAnalysis(int, String, Collection)}, which looks at
     * {@code ticket.last_interacted_at}, the window is defined by ticket creation time
     * ({@code query.date}) — the same semantics the Support Summary page reports on, so the gaps
     * this returns are exactly the gaps in that page's breakdowns.
     *
     * @param from First day of the window (inclusive)
     * @param to Last day of the window (inclusive)
     * @param promptId The current prompt ID to check against existing analysis records
     * @param channelIds Slack channel IDs to include; tickets from any of these channels are returned
     * @return Immutable list of threads that need analysis
     */
    ImmutableList<ThreadToAnalyze> findThreadsAwaitingAnalysis(
            LocalDate from, LocalDate to, String promptId, Collection<String> channelIds);

    /**
     * DTO representing a thread that needs analysis.
     *
     * @param ticketId The ticket ID
     * @param threadTs The Slack thread timestamp
     * @param channelId The Slack channel the thread lives in
     */
    record ThreadToAnalyze(Long ticketId, String threadTs, String channelId) {}
}
