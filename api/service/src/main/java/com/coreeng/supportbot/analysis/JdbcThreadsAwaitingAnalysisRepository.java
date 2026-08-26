package com.coreeng.supportbot.analysis;

import static com.coreeng.supportbot.dbschema.Tables.ANALYSIS;
import static com.coreeng.supportbot.dbschema.Tables.QUERY;
import static com.coreeng.supportbot.dbschema.Tables.TICKET;
import static org.jooq.impl.DSL.cast;
import static org.jooq.impl.DSL.notExists;
import static org.jooq.impl.DSL.selectOne;
import static org.jooq.impl.DSL.val;

import com.coreeng.supportbot.dbschema.enums.TicketStatus;
import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
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
 *
 * <p>Both overloads run the same query; only the time predicate differs, so the two callers cannot
 * drift apart on the status/channel/prompt filters.
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

        // Postgres-specific interval arithmetic kept as a typed, parameterised plain-SQL fragment
        // (there is no portable jOOQ DSL equivalent): midnight today minus `days` days.
        Field<Instant> cutoff = DSL.field("now()::date - ({0} * interval '1 day')", Instant.class, DSL.val(days));

        return find(TICKET.LAST_INTERACTED_AT.gt(cutoff), promptId, channelIds);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The window is applied to {@code query.date} as a half-open interval with the bound — not the
     * column — cast, keeping the predicate sargable against {@code query_date_idx}.
     */
    @Override
    @Transactional(readOnly = true)
    public ImmutableList<ThreadToAnalyze> findThreadsAwaitingAnalysis(
            LocalDate from, LocalDate to, String promptId, Collection<String> channelIds) {
        log.info(
                "Finding threads awaiting analysis: channelIds={}, from={}, to={}, promptId={}",
                channelIds,
                from,
                to,
                promptId);

        Condition window = QUERY.DATE
                .ge(cast(val(from), QUERY.DATE.getDataType()))
                .and(QUERY.DATE.lt(cast(val(to.plusDays(1)), QUERY.DATE.getDataType())));

        return find(window, promptId, channelIds);
    }

    private ImmutableList<ThreadToAnalyze> find(Condition timeWindow, String promptId, Collection<String> channelIds) {
        if (channelIds.isEmpty()) {
            return ImmutableList.of();
        }

        ImmutableList<ThreadToAnalyze> threads = dsl
                .selectDistinct(TICKET.ID, QUERY.TS, QUERY.CHANNEL_ID)
                .from(QUERY)
                .join(TICKET)
                .on(TICKET.QUERY_ID.eq(QUERY.ID))
                .where(TICKET.STATUS.eq(TicketStatus.closed))
                .and(QUERY.CHANNEL_ID.in(channelIds))
                .and(timeWindow)
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
