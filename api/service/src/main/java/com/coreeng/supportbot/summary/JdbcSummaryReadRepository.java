package com.coreeng.supportbot.summary;

import static com.coreeng.supportbot.dbschema.Tables.ANALYSIS;
import static com.coreeng.supportbot.dbschema.Tables.QUERY;
import static com.coreeng.supportbot.dbschema.Tables.TICKET;
import static org.jooq.impl.DSL.cast;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.countDistinct;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.nullif;
import static org.jooq.impl.DSL.partitionBy;
import static org.jooq.impl.DSL.rowNumber;
import static org.jooq.impl.DSL.trim;
import static org.jooq.impl.DSL.val;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record2;
import org.jooq.Record5;
import org.jooq.Result;
import org.jooq.SelectOnConditionStep;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * jOOQ implementation of {@link SummaryReadRepository}.
 *
 * <p>Notes that apply to every query here:
 * <ul>
 *   <li>The window is a half-open interval on {@code query.date} with the bound cast rather than the
 *       column, so it stays sargable against {@code query_date_idx}.
 *   <li>{@code ticket.id} is a {@code bigint} while {@code analysis.ticket_id} is an {@code int},
 *       so the join coerces one side.
 *   <li>Blank dimension values are bucketed under an explicit label instead of being dropped.
 * </ul>
 */
@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JdbcSummaryReadRepository implements SummaryReadRepository {

    /** Bucket for a classified ticket whose driver or category came back blank. */
    private static final String UNCLASSIFIED_LABEL = "Unclassified";

    /** Bucket for a ticket the classifier could not tie to a platform feature. */
    private static final String NO_FEATURE_LABEL = "None";

    /** Bucket for a ticket with no team recorded on it. */
    private static final String UNKNOWN_TEAM_LABEL = "Unknown";

    /** How many example tickets each breakdown row carries — the same cap the knowledge-gaps page uses. */
    static final int RECENT_PER_ROW = 5;

    private final DSLContext dsl;

    @Override
    public SummaryBreakdowns breakdowns(SummaryWindow window, String promptId, Collection<String> channelIds) {
        if (channelIds.isEmpty()) {
            return new SummaryBreakdowns(
                    window, 0L, 0L, ImmutableList.of(), ImmutableList.of(), ImmutableList.of(), ImmutableList.of());
        }

        long totalTickets = orZero(dsl.selectCount()
                .from(QUERY)
                .join(TICKET)
                .on(TICKET.QUERY_ID.eq(QUERY.ID))
                .where(inWindow(window, channelIds))
                .fetchOne(0, Long.class));

        long classifiedTickets = orZero(dsl.select(countDistinct(TICKET.ID))
                .from(QUERY)
                .join(TICKET)
                .on(TICKET.QUERY_ID.eq(QUERY.ID))
                .join(ANALYSIS)
                .on(analysisJoin(promptId))
                .where(inWindow(window, channelIds))
                .fetchOne(0, Long.class));

        return new SummaryBreakdowns(
                window,
                totalTickets,
                classifiedTickets,
                countByAnalysisField(ANALYSIS.DRIVER, UNCLASSIFIED_LABEL, window, promptId, channelIds),
                countByAnalysisField(ANALYSIS.CATEGORY, UNCLASSIFIED_LABEL, window, promptId, channelIds),
                countByAnalysisField(ANALYSIS.FEATURE, NO_FEATURE_LABEL, window, promptId, channelIds),
                countByTeam(window, promptId, channelIds));
    }

    @Override
    public SummaryFingerprint fingerprint(SummaryWindow window, String promptId, Collection<String> channelIds) {
        if (channelIds.isEmpty()) {
            return new SummaryFingerprint(0L, null);
        }

        Record2<Integer, LocalDateTime> row = dsl.select(count(), max(ANALYSIS.UPDATED_AT))
                .from(QUERY)
                .join(TICKET)
                .on(TICKET.QUERY_ID.eq(QUERY.ID))
                .join(ANALYSIS)
                .on(analysisJoin(promptId))
                .where(inWindow(window, channelIds))
                .fetchSingle();

        Integer rowCount = row.value1();
        return new SummaryFingerprint(rowCount == null ? 0L : rowCount.longValue(), row.value2());
    }

    @Override
    public ImmutableList<String> reasons(
            SummaryWindow window, String promptId, Collection<String> channelIds, int limit) {
        if (channelIds.isEmpty() || limit <= 0) {
            return ImmutableList.of();
        }

        return dsl
                .select(ANALYSIS.SUMMARY)
                .from(QUERY)
                .join(TICKET)
                .on(TICKET.QUERY_ID.eq(QUERY.ID))
                .join(ANALYSIS)
                .on(analysisJoin(promptId))
                .where(inWindow(window, channelIds))
                .and(trim(ANALYSIS.SUMMARY).ne(""))
                .orderBy(QUERY.DATE.desc(), TICKET.ID.desc())
                .limit(limit)
                .fetch(ANALYSIS.SUMMARY)
                .stream()
                .collect(ImmutableList.toImmutableList());
    }

    private ImmutableList<SummaryCount> countByAnalysisField(
            TableField<?, String> field,
            String blankLabel,
            SummaryWindow window,
            String promptId,
            Collection<String> channelIds) {
        Field<String> label = bucketed(field, blankLabel);

        Result<Record2<String, Integer>> rows = dsl.select(label, count())
                .from(QUERY)
                .join(TICKET)
                .on(TICKET.QUERY_ID.eq(QUERY.ID))
                .join(ANALYSIS)
                .on(analysisJoin(promptId))
                .where(inWindow(window, channelIds))
                .groupBy(label)
                .orderBy(count().desc(), label.asc())
                .fetch();

        return toCounts(rows, recentBy(label, true, window, promptId, channelIds));
    }

    private ImmutableList<SummaryCount> countByTeam(
            SummaryWindow window, String promptId, Collection<String> channelIds) {
        Field<String> label = bucketed(TICKET.TEAM, UNKNOWN_TEAM_LABEL);

        Result<Record2<String, Integer>> rows = dsl.select(label, count())
                .from(QUERY)
                .join(TICKET)
                .on(TICKET.QUERY_ID.eq(QUERY.ID))
                .where(inWindow(window, channelIds))
                .groupBy(label)
                .orderBy(count().desc(), label.asc())
                .fetch();

        return toCounts(rows, recentBy(label, false, window, promptId, channelIds));
    }

    /**
     * The newest {@value #RECENT_PER_ROW} tickets per bucket of {@code label}, newest first. One
     * query: rank the window's tickets within each bucket, then keep the top of each partition.
     *
     * @param classifiedOnly true for the analysis-derived breakdowns, whose rows only exist for
     *     classified tickets; false for teams, where a not-yet-classified ticket is still an example
     *     (with a blank reason)
     */
    private ImmutableMap<String, ImmutableList<SummaryTicketExample>> recentBy(
            Field<String> label,
            boolean classifiedOnly,
            SummaryWindow window,
            String promptId,
            Collection<String> channelIds) {
        Field<String> bucket = label.as("bucket");
        Field<Long> ticketId = TICKET.ID.as("ticket_id");
        Field<String> reason = ANALYSIS.SUMMARY.as("reason");
        Field<Instant> raisedAt = QUERY.DATE.as("raised_at");
        Field<Integer> rank = rowNumber()
                .over(partitionBy(label).orderBy(QUERY.DATE.desc(), TICKET.ID.desc()))
                .as("rank");

        SelectOnConditionStep<Record5<String, Long, String, Instant, Integer>> joined = dsl.select(
                        bucket, ticketId, reason, raisedAt, rank)
                .from(QUERY)
                .join(TICKET)
                .on(TICKET.QUERY_ID.eq(QUERY.ID));
        Table<Record5<String, Long, String, Instant, Integer>> ranked = (classifiedOnly
                        ? joined.join(ANALYSIS).on(analysisJoin(promptId))
                        : joined.leftJoin(ANALYSIS).on(analysisJoin(promptId)))
                .where(inWindow(window, channelIds))
                .asTable("ranked");

        Map<String, ImmutableList.Builder<SummaryTicketExample>> byBucket = new LinkedHashMap<>();
        dsl.selectFrom(ranked)
                // Unqualified names resolve against the derived table; jOOQ's typed field(...) lookup is
                // nullable, which NullAway would reject.
                .where(field(name("rank"), Integer.class).le(RECENT_PER_ROW))
                .orderBy(
                        field(name("bucket"), String.class).asc(),
                        field(name("rank"), Integer.class).asc())
                .fetch()
                .forEach(row -> {
                    String key = row.get(bucket);
                    Long id = row.get(ticketId);
                    Instant at = row.get(raisedAt);
                    if (key == null || id == null || at == null) {
                        return;
                    }
                    String text = row.get(reason);
                    byBucket.computeIfAbsent(key, _ -> ImmutableList.builder())
                            .add(new SummaryTicketExample(id, text == null ? "" : text, at));
                });

        ImmutableMap.Builder<String, ImmutableList<SummaryTicketExample>> result = ImmutableMap.builder();
        byBucket.forEach((key, examples) -> result.put(key, examples.build()));
        return result.build();
    }

    private static ImmutableList<SummaryCount> toCounts(
            Result<Record2<String, Integer>> rows, ImmutableMap<String, ImmutableList<SummaryTicketExample>> recent) {
        ImmutableList.Builder<SummaryCount> counts = ImmutableList.builder();
        for (Record2<String, Integer> row : rows) {
            String label = row.value1();
            Integer count = row.value2();
            if (label != null && count != null) {
                counts.add(new SummaryCount(label, count.longValue(), recent.getOrDefault(label, ImmutableList.of())));
            }
        }
        return counts.build();
    }

    /**
     * Replaces null and whitespace-only values with an explicit bucket label. Grouping on the raw
     * column would scatter " " and "" into separate rows and hide them behind an empty legend entry.
     *
     * <p>The labels are inlined rather than bound: Postgres matches a GROUP BY expression against the
     * select list structurally, and two occurrences of the same expression built from separate bind
     * placeholders do not match ("column must appear in the GROUP BY clause"). Both values are
     * compile-time constants of this class, so inlining introduces no injection surface.
     */
    private static Field<String> bucketed(TableField<?, String> field, String blankLabel) {
        return coalesce(nullif(trim(field), DSL.inline("")), DSL.inline(blankLabel));
    }

    /** An aggregate over an empty result set still yields a row, but jOOQ's typed accessor is nullable. */
    private static long orZero(@Nullable Long value) {
        return value == null ? 0L : value;
    }

    private static Condition analysisJoin(String promptId) {
        return ANALYSIS.TICKET_ID.eq(TICKET.ID.coerce(ANALYSIS.TICKET_ID)).and(ANALYSIS.PROMPT_ID.eq(promptId));
    }

    private static Condition inWindow(SummaryWindow window, Collection<String> channelIds) {
        return QUERY.CHANNEL_ID
                .in(channelIds)
                .and(QUERY.DATE.ge(cast(val(window.from()), QUERY.DATE.getDataType())))
                .and(QUERY.DATE.lt(cast(val(window.to().plusDays(1)), QUERY.DATE.getDataType())));
    }
}
