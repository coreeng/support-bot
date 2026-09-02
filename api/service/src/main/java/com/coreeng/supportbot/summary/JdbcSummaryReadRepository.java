package com.coreeng.supportbot.summary;

import static com.coreeng.supportbot.dbschema.Tables.ANALYSIS;
import static com.coreeng.supportbot.dbschema.Tables.QUERY;
import static com.coreeng.supportbot.dbschema.Tables.TAG;
import static com.coreeng.supportbot.dbschema.Tables.TICKET;
import static com.coreeng.supportbot.dbschema.Tables.TICKET_TO_TAG;
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

import com.coreeng.supportbot.dbschema.enums.TicketStatus;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record2;
import org.jooq.Record4;
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
 *   <li>The window is a half-open interval on {@code query.date}, bound as UTC instants (the page's
 *       dates are UTC days, so the bounds must not move with the session time zone) and compared
 *       against the bare column, so it stays sargable against {@code query_date_idx}.
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

        // Every ticket raised in the window, and how many of them carry an analysis for this prompt.
        Record2<Integer, Integer> totals = dsl.select(countDistinct(TICKET.ID), countDistinct(ANALYSIS.TICKET_ID))
                .from(QUERY)
                .join(TICKET)
                .on(TICKET.QUERY_ID.eq(QUERY.ID))
                .leftJoin(ANALYSIS)
                .on(analysisJoin(promptId))
                .where(inWindow(window, channelIds))
                .fetchSingle();

        return new SummaryBreakdowns(
                window,
                orZero(totals.value1()),
                orZero(totals.value2()),
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
        return breakdown(
                bucketed(field, blankLabel),
                j -> j.join(ANALYSIS).on(analysisJoin(promptId)),
                filter,
                ImmutableMap.of(),
                window,
                channelIds);
    }

    private ImmutableList<SummaryCount> countByTeam(
            SummaryWindow window, String promptId, Collection<String> channelIds) {
        Field<String> label = bucketed(TICKET.TEAM, UNKNOWN_TEAM_LABEL);
        return breakdown(
                label,
                j -> j.leftJoin(ANALYSIS).on(analysisJoin(promptId)),
                noCondition(),
                topProductByTeam(label, window, channelIds),
                window,
                channelIds);
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
        Joins viaProductTag = j -> j.join(TICKET_TO_TAG)
                .on(TICKET_TO_TAG.TICKET_ID.eq(TICKET.ID))
                .join(TAG)
                .on(TAG.CODE.eq(TICKET_TO_TAG.TAG_CODE))
                .leftJoin(ANALYSIS)
                .on(analysisJoin(promptId));
        return breakdown(label, viaProductTag, isProductTag(label), ImmutableMap.of(), window, channelIds);
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

    /**
     * How a breakdown reaches its tickets from {@code query ⋈ ticket}: which tables to add and whether
     * analysis is required.
     */
    private interface Joins extends UnaryOperator<SelectOnConditionStep<Record4<String, Long, String, Instant>>> {}

    /**
     * One breakdown in one statement: every bucket of {@code label} with its ticket count and its
     * newest {@value #RECENT_PER_ROW} tickets, largest bucket first (ties alphabetical), examples
     * newest first.
     *
     * <p>Three layers. The innermost lists the window's distinct (bucket, ticket) pairs — a ticket
     * reaches a product bucket once per tag naming that product, and must count once. The middle
     * numbers each bucket's tickets newest first and sizes the bucket, both as window functions over
     * those pairs. The outer keeps only the top-numbered rows of each bucket; every one of them still
     * carries the bucket's size, which is how the count arrives without a GROUP BY of its own.
     *
     * @param joins the breakdown's own joins beyond {@code query ⋈ ticket}; an inner join on
     *     {@code analysis} limits the breakdown to classified tickets, a left join lets a
     *     not-yet-classified ticket appear with a blank reason
     * @param filter narrows the tickets (e.g. to one driver); {@link DSL#noCondition()} for the whole window
     * @param topProduct the sub-line under each bucket, for the teams breakdown; empty for the others
     */
    private ImmutableList<SummaryCount> breakdown(
            Field<String> label,
            Joins joins,
            Condition filter,
            ImmutableMap<String, String> topProduct,
            SummaryWindow window,
            Collection<String> channelIds) {
        // Unqualified names resolve against the enclosing derived table; jOOQ's typed field(...) lookup
        // on a Table is nullable, which NullAway would reject.
        Field<String> bucket = field(name("bucket"), String.class);
        Field<Long> ticketId = field(name("ticket_id"), Long.class);
        Field<String> reason = field(name("reason"), String.class);
        Field<Instant> raisedAt = field(name("raised_at"), Instant.class);
        Field<Integer> rank = field(name("rank"), Integer.class);
        Field<Integer> size = field(name("bucket_size"), Integer.class);

        SelectOnConditionStep<Record4<String, Long, String, Instant>> joined = dsl.selectDistinct(
                        label.as(bucket), TICKET.ID.as(ticketId), ANALYSIS.SUMMARY.as(reason), QUERY.DATE.as(raisedAt))
                .from(QUERY)
                .join(TICKET)
                .on(TICKET.QUERY_ID.eq(QUERY.ID));
        Table<?> tickets = joins.apply(joined)
                .where(inWindow(window, channelIds))
                .and(filter)
                .asTable("tickets");

        Table<?> ranked = dsl.select(
                        bucket,
                        ticketId,
                        reason,
                        raisedAt,
                        rowNumber()
                                .over(partitionBy(bucket).orderBy(raisedAt.desc(), ticketId.desc()))
                                .as(rank),
                        count().over(partitionBy(bucket)).as(size))
                .from(tickets)
                .asTable("ranked");

        Map<String, Long> sizes = new HashMap<>();
        Map<String, ImmutableList.Builder<SummaryTicketExample>> examples = new LinkedHashMap<>();
        dsl.selectFrom(ranked)
                .where(rank.le(RECENT_PER_ROW))
                .orderBy(size.desc(), bucket.asc(), rank.asc())
                .fetch()
                .forEach(row -> {
                    String key = row.get(bucket);
                    Long id = row.get(ticketId);
                    Instant at = row.get(raisedAt);
                    Integer bucketSize = row.get(size);
                    if (key == null || id == null || at == null || bucketSize == null) {
                        return;
                    }
                    String text = row.get(reason);
                    sizes.putIfAbsent(key, bucketSize.longValue());
                    examples.computeIfAbsent(key, _ -> ImmutableList.builder())
                            .add(new SummaryTicketExample(id, text == null ? "" : text, at));
                });

        ImmutableList.Builder<SummaryCount> counts = ImmutableList.builder();
        examples.forEach((key, recent) ->
                counts.add(new SummaryCount(key, sizes.getOrDefault(key, 0L), recent.build(), topProduct.get(key))));
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
    private static long orZero(@Nullable Integer value) {
        return value == null ? 0L : value.longValue();
    }

    private static Condition analysisJoin(String promptId) {
        return ANALYSIS.TICKET_ID.eq(TICKET.ID.coerce(ANALYSIS.TICKET_ID)).and(ANALYSIS.PROMPT_ID.eq(promptId));
    }

    private static Condition inWindow(SummaryWindow window, Collection<String> channelIds) {
        Instant from = window.from().atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to = window.to().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return QUERY.CHANNEL_ID.in(channelIds).and(QUERY.DATE.ge(from)).and(QUERY.DATE.lt(to));
    }
}
