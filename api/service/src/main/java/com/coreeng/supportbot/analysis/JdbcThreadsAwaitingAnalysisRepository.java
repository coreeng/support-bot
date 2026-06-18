package com.coreeng.supportbot.analysis;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JOOQ-based implementation of {@link ThreadsAwaitingAnalysisRepository}.
 *
 * <p>Uses raw SQL to find threads that need analysis by:
 * <ol>
 *   <li>Joining {@code query} and {@code ticket} tables</li>
 *   <li>Filtering for closed tickets in the specified time range</li>
 *   <li>Excluding tickets that already have an analysis record with the current prompt ID</li>
 * </ol>
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class JdbcThreadsAwaitingAnalysisRepository implements ThreadsAwaitingAnalysisRepository {

    private final DSLContext dsl;

    /**
     * {@inheritDoc}
     *
     * <p>Implementation uses a SQL query with a NOT EXISTS subquery to exclude tickets
     * that already have analysis records for the given prompt ID.
     */
    @Override
    @Transactional(readOnly = true)
    public ImmutableList<ThreadToAnalyze> findThreadsAwaitingAnalysis(
            int days, String promptId, Collection<String> channelIds) {
        log.info("Finding threads awaiting analysis: channelIds={}, days={}, promptId={}", channelIds, days, promptId);

        if (channelIds.isEmpty()) {
            return ImmutableList.of();
        }

        String channelPlaceholders = channelIds.stream().map(c -> "?").collect(Collectors.joining(", "));
        String sql = """
            SELECT DISTINCT
                t.id as ticket_id,
                q.ts as thread_ts,
                q.channel_id as channel_id
            FROM query q
                JOIN ticket t ON t.query_id = q.id
            WHERE t.status = 'closed'
              AND q.channel_id IN (%s)
              AND t.last_interacted_at > NOW()::date - (? * INTERVAL '1 days')
              AND NOT EXISTS (SELECT 1 FROM analysis WHERE ticket_id = t.id AND prompt_id = ?)
            """.formatted(channelPlaceholders);

        List<Object> binds = new ArrayList<>(channelIds);
        binds.add(days);
        binds.add(promptId);

        ImmutableList<ThreadToAnalyze> threads = dsl
                .resultQuery(sql, binds.toArray())
                .fetch(r -> new ThreadToAnalyze(
                        r.get("ticket_id", Long.class),
                        r.get("thread_ts", String.class),
                        r.get("channel_id", String.class)))
                .stream()
                .collect(ImmutableList.toImmutableList());

        log.info("Found {} threads awaiting analysis", threads.size());
        return threads;
    }
}
