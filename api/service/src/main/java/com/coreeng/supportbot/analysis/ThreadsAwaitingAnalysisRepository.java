package com.coreeng.supportbot.analysis;

import com.google.common.collect.ImmutableList;
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
     * Finds threads from closed tickets in the last N days that don't have an analysis record
     * with the given prompt ID.
     *
     * @param days Number of days to look back from today
     * @param promptId The current prompt ID to check against existing analysis records
     * @param channelIds Slack channel IDs to include; tickets from any of these channels are returned
     * @return Immutable list of threads that need analysis
     */
    ImmutableList<ThreadToAnalyze> findThreadsAwaitingAnalysis(
            int days, String promptId, Collection<String> channelIds);

    /**
     * DTO representing a thread that needs analysis.
     *
     * @param ticketId The ticket ID
     * @param threadTs The Slack thread timestamp
     * @param channelId The Slack channel the thread lives in
     */
    record ThreadToAnalyze(Long ticketId, String threadTs, String channelId) {}
}
