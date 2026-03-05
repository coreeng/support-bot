package com.coreeng.supportbot.analysis;

import com.coreeng.supportbot.analysis.ThreadsAwaitingAnalysisRepository.ThreadToAnalyze;
import com.coreeng.supportbot.config.SlackTicketsProps;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Service for finding threads from closed tickets that need LLM analysis.
 *
 * <p>This service identifies tickets eligible for analysis based on:
 *
 * <ul>
 *   <li>Ticket status (must be closed)
 *   <li>Time range (within the last N days)
 *   <li>Missing analysis for the current prompt ID (no analysis record with matching {@code prompt_id})
 * </ul>
 *
 * <p>The service delegates to {@link ThreadsAwaitingAnalysisRepository} for data access
 * and automatically injects the configured Slack channel ID from {@link SlackTicketsProps}.
 */
@Service
@RequiredArgsConstructor
public class ThreadsAwaitingAnalysisService {

    private final ThreadsAwaitingAnalysisRepository repository;
    private final SlackTicketsProps slackTicketsProps;

    /**
     * Finds threads from closed tickets in the last N days that don't have an analysis record
     * with the given prompt ID.
     *
     * @param days Number of days to look back from today
     * @param promptId The current prompt ID to check against existing analysis records
     * @return Immutable list of threads that need analysis
     */
    public ImmutableList<ThreadToAnalyze> find(int days, String promptId) {
        return repository.findThreadsAwaitingAnalysis(days, promptId, slackTicketsProps.channelId());
    }
}
