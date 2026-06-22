package com.coreeng.supportbot.analysis;

import static com.coreeng.supportbot.dbschema.Tables.ANALYSIS;
import static com.coreeng.supportbot.dbschema.Tables.QUERY;
import static com.coreeng.supportbot.dbschema.Tables.TICKET;
import static org.jooq.impl.DSL.notExists;
import static org.jooq.impl.DSL.selectOne;

import com.coreeng.supportbot.dbschema.enums.TicketStatus;
import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JOOQ-based implementation of {@link ThreadsAwaitingAnalysisRepository}.
 *
 * <p>Finds threads that need analysis by:
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
     * <p>Implementation uses a {@code NOT EXISTS} subquery to exclude tickets that already have
     * analysis records for the given prompt ID.
     */
    @Override
    @Transactional(readOnly = true)
    public ImmutableList<ThreadToAnalyze> findThreadsAwaitingAnalysis(
            int days, String promptId, Collection<String> channelIds) {
        log.info("Finding threads awaiting analysis: channelIds={}, days={}, promptId={}", channelIds, days, promptId);

        if (channelIds.isEmpty()) {
            return ImmutableList.of();
        }

        // Postgres-specific interval arithmetic kept as a typed, parameterised plain-SQL fragment
        // (there is no portable jOOQ DSL equivalent): midnight today minus `days` days.
        Field<Instant> cutoff = DSL.field("now()::date - ({0} * interval '1 day')", Instant.class, DSL.val(days));

        ImmutableList<ThreadToAnalyze> threads = dsl
                .selectDistinct(TICKET.ID, QUERY.TS, QUERY.CHANNEL_ID)
                .from(QUERY)
                .join(TICKET)
                .on(TICKET.QUERY_ID.eq(QUERY.ID))
                .where(TICKET.STATUS.eq(TicketStatus.closed))
                .and(QUERY.CHANNEL_ID.in(channelIds))
                .and(TICKET.LAST_INTERACTED_AT.gt(cutoff))
                .and(notExists(selectOne()
                        .from(ANALYSIS)
                        .where(ANALYSIS.TICKET_ID.eq(TICKET.ID.coerce(ANALYSIS.TICKET_ID)))
                        .and(ANALYSIS.PROMPT_ID.eq(promptId))))
                .fetch(r -> new ThreadToAnalyze(r.get(TICKET.ID), r.get(QUERY.TS), r.get(QUERY.CHANNEL_ID)))
                .stream()
                .collect(ImmutableList.toImmutableList());

        log.info("Found {} threads awaiting analysis", threads.size());
        return threads;
    }
}
