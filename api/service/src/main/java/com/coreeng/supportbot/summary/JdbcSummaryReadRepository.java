package com.coreeng.supportbot.summary;

import static com.coreeng.supportbot.dbschema.Tables.ANALYSIS;
import static com.coreeng.supportbot.dbschema.Tables.QUERY;
import static com.coreeng.supportbot.dbschema.Tables.TAG;
import static com.coreeng.supportbot.dbschema.Tables.TICKET;
import static com.coreeng.supportbot.dbschema.Tables.TICKET_TO_TAG;
import static org.jooq.impl.DSL.cast;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.countDistinct;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.notExists;
import static org.jooq.impl.DSL.nullif;
import static org.jooq.impl.DSL.partitionBy;
import static org.jooq.impl.DSL.regexpReplaceFirst;
import static org.jooq.impl.DSL.rowNumber;
import static org.jooq.impl.DSL.selectOne;
import static org.jooq.impl.DSL.sum;
import static org.jooq.impl.DSL.trim;
import static org.jooq.impl.DSL.val;

import com.coreeng.supportbot.dbschema.enums.TicketStatus;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
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

    /**
     * The driver label the classification prompt assigns to "tenant did not know the platform already
     * does this" tickets; the same literal the knowledge-gaps page filters on.
     */
    static final String KNOWLEDGE_GAP_DRIVER = "Knowledge Gap";

    /**
     * Product tags are recognised by their label prefix ("Product - &lt;name&gt;"), exactly as the
     * Products View does in the UI: case-insensitive, any dash, and a label that is only the prefix
     * is not a product. Postgres ARE syntax, with the case flag embedded so the same literal serves
     * both the match and the strip.
     */
    static final String PRODUCT_TAG_PREFIX = "(?i)^\\s*product\\s*[-\u2013\u2014]\\s*";

    /** How many example tickets each breakdown row carries — the same cap the knowledge-gaps page uses. */
    static final int RECENT_PER_ROW = 5;

    private final DSLContext dsl;

    @Override
    public SummaryBreakdowns breakdowns(SummaryWindow window, String promptId, Collection<String> channelIds) {
        if (channelIds.isEmpty()) {
            return new SummaryBreakdowns(
                    window,
                    0L,
                    0L,
                    ImmutableList.of(),
                    ImmutableList.of(),
                    ImmutableList.of(),
                    ImmutableList.of(),
                    ImmutableList.of(),
                    ImmutableList.of());
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
                countByAnalysisField(ANALYSIS.DRIVER, UNCLASSIFIED_LABEL, noCondition(), window, promptId, channelIds),
                countByAnalysisField(
                        ANALYSIS.CATEGORY, UNCLASSIFIED_LABEL, noCondition(), window, promptId, channelIds),
                countByAnalysisField(
                        ANALYSIS.CATEGORY,
                        UNCLASSIFIED_LABEL,
                        trim(ANALYSIS.DRIVER).eq(KNOWLEDGE_GAP_DRIVER),
                        window,
                        promptId,
                        channelIds),
                countByAnalysisField(ANALYSIS.FEATURE, NO_FEATURE_LABEL, noCondition(), window, promptId, channelIds),
                countByTeam(window, promptId, channelIds),
                countByProduct(window, promptId, channelIds));
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

        // Closed tickets with no analysis for this prompt: what the backfill would try to classify.
        Record2<Integer, BigDecimal> gaps = dsl.select(count(), coalesce(sum(TICKET.ID), BigDecimal.ZERO))
                .from(QUERY)
                .join(TICKET)
                .on(TICKET.QUERY_ID.eq(QUERY.ID))
                .where(inWindow(window, channelIds))
                .and(TICKET.STATUS.eq(TicketStatus.closed))
                .and(notExists(selectOne().from(ANALYSIS).where(analysisJoin(promptId))))
                .fetchSingle();

        Integer rowCount = row.value1();
        Integer gapCount = gaps.value1();
        BigDecimal gapIdSum = gaps.value2();
        return new SummaryFingerprint(
                rowCount == null ? 0L : rowCount.longValue(),
                row.value2(),
                gapCount == null ? 0L : gapCount.longValue(),
                gapIdSum == null ? 0L : gapIdSum.longValue());
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

    /**
     * @param filter narrows the classified tickets being counted (e.g. to one driver);
     *     {@link DSL#noCondition()} for the whole window
     */
    private ImmutableList<SummaryCount> countByAnalysisField(
            TableField<?, String> field,
            String blankLabel,
            Condition filter,
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
                .and(filter)
                .groupBy(label)
                .orderBy(count().desc(), label.asc())
                .fetch();

        return toCounts(
                rows, recentBy(label, j -> j.join(ANALYSIS).on(analysisJoin(promptId)), filter, window, channelIds));
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

        return toCounts(
                rows,
                topProductByTeam(label, window, channelIds),
                recentBy(
                        label,
                        j -> j.leftJoin(ANALYSIS).on(analysisJoin(promptId)),
                        noCondition(),
                        window,
                        channelIds));
    }

    /**
     * Tickets per product, read from the product tags on each ticket. A ticket counts once per distinct
     * product however many of its tags name it; a ticket with no product tag has no row, so this
     * breakdown reconciles against neither total. Tags are matched whether or not they have since been
     * retired, so history stays attributed — the same rules as the UI's Products View.
     */
    private ImmutableList<SummaryCount> countByProduct(
            SummaryWindow window, String promptId, Collection<String> channelIds) {
        Field<String> label = productName();
        Condition isProduct = isProductTag(label);

        Result<Record2<String, Integer>> rows = dsl.select(label, countDistinct(TICKET.ID))
                .from(QUERY)
                .join(TICKET)
                .on(TICKET.QUERY_ID.eq(QUERY.ID))
                .join(TICKET_TO_TAG)
                .on(TICKET_TO_TAG.TICKET_ID.eq(TICKET.ID))
                .join(TAG)
                .on(TAG.CODE.eq(TICKET_TO_TAG.TAG_CODE))
                .where(inWindow(window, channelIds))
                .and(isProduct)
                .groupBy(label)
                .orderBy(countDistinct(TICKET.ID).desc(), label.asc())
                .fetch();

        Joins viaProductTag = j -> j.join(TICKET_TO_TAG)
                .on(TICKET_TO_TAG.TICKET_ID.eq(TICKET.ID))
                .join(TAG)
                .on(TAG.CODE.eq(TICKET_TO_TAG.TAG_CODE))
                .leftJoin(ANALYSIS)
                .on(analysisJoin(promptId));
        return toCounts(rows, recentBy(label, viaProductTag, isProduct, window, channelIds));
    }

    /**
     * Each team's most-tagged product in the window, for the sub-line under the team's name. Ties
     * break alphabetically; a team none of whose tickets carries a product tag is absent.
     */
    private ImmutableMap<String, String> topProductByTeam(
            Field<String> team, SummaryWindow window, Collection<String> channelIds) {
        Field<String> product = productName();
        Field<Integer> tickets = countDistinct(TICKET.ID);

        Map<String, String> top = new LinkedHashMap<>();
        dsl.select(team, product, tickets)
                .from(QUERY)
                .join(TICKET)
                .on(TICKET.QUERY_ID.eq(QUERY.ID))
                .join(TICKET_TO_TAG)
                .on(TICKET_TO_TAG.TICKET_ID.eq(TICKET.ID))
                .join(TAG)
                .on(TAG.CODE.eq(TICKET_TO_TAG.TAG_CODE))
                .where(inWindow(window, channelIds))
                .and(isProductTag(product))
                .groupBy(team, product)
                .orderBy(team.asc(), tickets.desc(), product.asc())
                .fetch()
                .forEach(row -> {
                    String key = row.value1();
                    String name = row.value2();
                    if (key != null && name != null) {
                        top.putIfAbsent(key, name);
                    }
                });
        return ImmutableMap.copyOf(top);
    }

    /** The product a tag names: its label with the "Product - " prefix removed. Inlined for GROUP BY, as in {@link #bucketed}. */
    private static Field<String> productName() {
        return trim(regexpReplaceFirst(TAG.LABEL, inline(PRODUCT_TAG_PREFIX), inline("")));
    }

    /** Whether the joined tag is a product tag: prefixed label with a non-empty product name. */
    private static Condition isProductTag(Field<String> productName) {
        return TAG.LABEL.likeRegex(inline(PRODUCT_TAG_PREFIX)).and(productName.ne(inline("")));
    }

    /** How a breakdown reaches its examples from {@code query ⋈ ticket}: which tables to add and whether analysis is required. */
    private interface Joins
            extends UnaryOperator<SelectOnConditionStep<Record5<String, Long, String, Instant, Integer>>> {}

    /**
     * The newest {@value #RECENT_PER_ROW} tickets per bucket of {@code label}, newest first. One
     * query: rank the window's tickets within each bucket, then keep the top of each partition.
     *
     * @param joins the breakdown's own joins beyond {@code query ⋈ ticket}; an inner join on
     *     {@code analysis} limits examples to classified tickets, a left join lets a not-yet-classified
     *     ticket appear with a blank reason
     * @param filter the same narrowing applied to the counts, so the examples match the rows
     */
    private ImmutableMap<String, ImmutableList<SummaryTicketExample>> recentBy(
            Field<String> label, Joins joins, Condition filter, SummaryWindow window, Collection<String> channelIds) {
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
        Table<Record5<String, Long, String, Instant, Integer>> ranked = joins.apply(joined)
                .where(inWindow(window, channelIds))
                .and(filter)
                .asTable("ranked");

        Map<String, ImmutableList.Builder<SummaryTicketExample>> byBucket = new LinkedHashMap<>();
        // A ticket can reach the same bucket twice (two tags naming one product); show it once.
        Set<String> seen = new HashSet<>();
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
                    if (!seen.add(key + '\u0000' + id)) {
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
        return toCounts(rows, ImmutableMap.of(), recent);
    }

    private static ImmutableList<SummaryCount> toCounts(
            Result<Record2<String, Integer>> rows,
            ImmutableMap<String, String> topProduct,
            ImmutableMap<String, ImmutableList<SummaryTicketExample>> recent) {
        ImmutableList.Builder<SummaryCount> counts = ImmutableList.builder();
        for (Record2<String, Integer> row : rows) {
            String label = row.value1();
            Integer count = row.value2();
            if (label != null && count != null) {
                counts.add(new SummaryCount(
                        label,
                        count.longValue(),
                        recent.getOrDefault(label, ImmutableList.of()),
                        topProduct.get(label)));
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
