package com.coreeng.supportbot.metrics;

import java.util.List;

import org.jooq.DSLContext;
import org.jooq.Field;

import static com.coreeng.supportbot.dbschema.Tables.*;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.exists;
import static org.jooq.impl.DSL.notExists;
import static org.jooq.impl.DSL.selectOne;
import static com.google.common.base.Preconditions.checkNotNull;

import com.coreeng.supportbot.dbschema.enums.EscalationStatus;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JdbcMetricsRepository implements MetricsRepository {

    private final DSLContext dsl;

    @Override
    public List<TicketMetric> getTicketMetrics() {
        Field<Boolean> escalated = exists(
            selectOne()
                .from(ESCALATION)
                .where(ESCALATION.TICKET_ID.eq(TICKET.ID))
                .and(ESCALATION.STATUS.ne(EscalationStatus.resolved))
        ).as("escalated");

        return dsl.select(TICKET.STATUS, TICKET.IMPACT_CODE, TICKET.TEAM, escalated, TICKET.RATING_SUBMITTED, count().as("count"))
            .from(TICKET)
            .groupBy(TICKET.STATUS, TICKET.IMPACT_CODE, TICKET.TEAM, escalated, TICKET.RATING_SUBMITTED)
            .fetch(r -> new TicketMetric(
                r.get(TICKET.STATUS).getLiteral(),
                r.get(TICKET.IMPACT_CODE) != null ? r.get(TICKET.IMPACT_CODE) : "unknown",
                r.get(TICKET.TEAM) != null ? r.get(TICKET.TEAM) : "unassigned",
                r.get(escalated),
                r.get(TICKET.RATING_SUBMITTED),
                r.get("count", Long.class)
            ));
    }

    @Override
    public List<EscalationMetric> getEscalationMetrics() {
        return dsl.select(ESCALATION.STATUS, ESCALATION.TEAM, TICKET.IMPACT_CODE, count().as("count"))
                .from(ESCALATION)
                .join(TICKET).on(ESCALATION.TICKET_ID.eq(TICKET.ID))
                .groupBy(ESCALATION.STATUS, ESCALATION.TEAM, TICKET.IMPACT_CODE)
                .fetch(r -> new EscalationMetric(
                        r.get(ESCALATION.STATUS).getLiteral(),
                        r.get(ESCALATION.TEAM) != null ? r.get(ESCALATION.TEAM) : "unknown",
                        r.get(TICKET.IMPACT_CODE) != null ? r.get(TICKET.IMPACT_CODE) : "unknown",
                        r.get("count", Long.class)
                ));
    }

    @Override
    public List<RatingMetric> getRatingMetrics() {
        return dsl.select(RATINGS.RATING, count().as("count"))
                .from(RATINGS)
                .groupBy(RATINGS.RATING)
                .fetch(r -> new RatingMetric(
                        r.get(RATINGS.RATING),
                        r.get("count", Long.class)
                ));
    }

    @Override
    public long getUnattendedQueryCount() {
        Long count = dsl.selectCount()
            .from(QUERY)
            .where(notExists(
                selectOne()
                    .from(TICKET)
                    .where(TICKET.QUERY_ID.eq(QUERY.ID))
            ))
            .fetchOne(0, Long.class);
        return count != null ? count : 0L;
    }

    @Override
    public ResponseSLAMetric getResponseSLAMetrics() {
        var result = dsl.resultQuery("""
            WITH response_durations AS (
                SELECT EXTRACT(EPOCH FROM business_time_between(
                    query_posted_ts,
                    first_open_ts,
                    'Europe/London'
                )) AS duration
                FROM aggregated_ticket_data
                WHERE first_open_ts IS NOT NULL
                  AND query_posted_ts IS NOT NULL
                  AND first_open_ts > query_posted_ts
            )
            SELECT
                percentile_cont(0.5) WITHIN GROUP (ORDER BY duration) AS p50,
                percentile_cont(0.9) WITHIN GROUP (ORDER BY duration) AS p90
            FROM response_durations
            WHERE duration IS NOT NULL AND duration > 0
            """).fetchOne();
        if (result == null) {
            return new ResponseSLAMetric(0.0, 0.0);
        }
        return new ResponseSLAMetric(
                result.get("p50", Double.class) != null ? result.get("p50", Double.class) : 0.0,
                result.get("p90", Double.class) != null ? result.get("p90", Double.class) : 0.0
        );
    }

    @Override
    public ResolutionSLAMetric getResolutionSLAMetrics() {
        var result = dsl.resultQuery("""
            WITH resolution_durations AS (
                SELECT EXTRACT(EPOCH FROM business_time_between(
                    first_open_ts,
                    last_closed_ts,
                    'Europe/London'
                )) AS duration
                FROM aggregated_ticket_data
                WHERE last_closed_ts IS NOT NULL
                  AND first_open_ts IS NOT NULL
                  AND last_closed_ts > first_open_ts
            )
            SELECT
                percentile_cont(0.5) WITHIN GROUP (ORDER BY duration) AS p50,
                percentile_cont(0.75) WITHIN GROUP (ORDER BY duration) AS p75,
                percentile_cont(0.9) WITHIN GROUP (ORDER BY duration) AS p90
            FROM resolution_durations
            WHERE duration IS NOT NULL AND duration > 0
            """).fetchOne();
        if (result == null) {
            return new ResolutionSLAMetric(0.0, 0.0, 0.0);
        }
        return new ResolutionSLAMetric(
                result.get("p50", Double.class) != null ? result.get("p50", Double.class) : 0.0,
                result.get("p75", Double.class) != null ? result.get("p75", Double.class) : 0.0,
                result.get("p90", Double.class) != null ? result.get("p90", Double.class) : 0.0
        );
    }

    @Override
    public List<EscalationByTagMetric> getEscalationsByTag() {
        return dsl.resultQuery("""
            SELECT
                unnest(et.tags) AS tag,
                COUNT(*) AS count
            FROM aggregated_escalation_data et
            WHERE et.open_ts IS NOT NULL
              AND et.tags IS NOT NULL
            GROUP BY tag
            HAVING COUNT(*) > 0
            ORDER BY count DESC
            """)
                .fetch(r -> new EscalationByTagMetric(
                        r.get("tag", String.class),
                        r.get("count", Long.class)
                ));
    }

    @Override
    public Double getLongestActiveTicketSeconds() {
        var result = dsl.resultQuery("""
            SELECT MAX(EXTRACT(EPOCH FROM (NOW() - first_open_ts))) AS max_age_seconds
            FROM aggregated_ticket_data
            WHERE status IN ('opened', 'stale')
              AND first_open_ts IS NOT NULL
            """).fetchOne();
        if (result == null) {
            return 0.0;
        }
        Double maxAge = result.get("max_age_seconds", Double.class);
        return maxAge != null ? maxAge : 0.0;
    }

    @Override
    // This has been taken from support-ui
    public List<WeeklyActivityMetric> getWeeklyActivity() {
        return dsl.resultQuery("""
            WITH current_week AS (
                SELECT
                    COUNT(DISTINCT CASE WHEN DATE_TRUNC('week', td.first_open_ts::timestamptz) = DATE_TRUNC('week', now()) THEN td.ticket_id END) AS opened,
                    COUNT(DISTINCT CASE WHEN DATE_TRUNC('week', td.last_closed_ts::timestamptz) = DATE_TRUNC('week', now()) THEN td.ticket_id END) AS closed,
                    COUNT(DISTINCT CASE WHEN td.status = 'stale' AND DATE_TRUNC('week', td.first_open_ts::timestamptz) = DATE_TRUNC('week', now()) THEN td.ticket_id END) AS stale,
                    COUNT(DISTINCT CASE WHEN DATE_TRUNC('week', ed.open_ts::timestamptz) = DATE_TRUNC('week', now()) THEN ed.escalation_id END) AS escalated
                FROM aggregated_ticket_data td
                LEFT JOIN aggregated_escalation_data ed ON td.ticket_id = ed.ticket_id
            ),
            last_week AS (
                SELECT
                    COUNT(DISTINCT CASE WHEN DATE_TRUNC('week', td.first_open_ts::timestamptz) = DATE_TRUNC('week', now() - interval '1 week') THEN td.ticket_id END) AS opened,
                    COUNT(DISTINCT CASE WHEN DATE_TRUNC('week', td.last_closed_ts::timestamptz) = DATE_TRUNC('week', now() - interval '1 week') THEN td.ticket_id END) AS closed,
                    COUNT(DISTINCT CASE WHEN td.status = 'stale' AND DATE_TRUNC('week', td.first_open_ts::timestamptz) = DATE_TRUNC('week', now() - interval '1 week') THEN td.ticket_id END) AS stale,
                    COUNT(DISTINCT CASE WHEN DATE_TRUNC('week', ed.open_ts::timestamptz) = DATE_TRUNC('week', now() - interval '1 week') THEN ed.escalation_id END) AS escalated
                FROM aggregated_ticket_data td
                LEFT JOIN aggregated_escalation_data ed ON td.ticket_id = ed.ticket_id
            )
            SELECT 'opened' AS type, 'current' AS week, cw.opened AS count FROM current_week cw
            UNION ALL SELECT 'opened', 'previous', lw.opened FROM last_week lw
            UNION ALL SELECT 'closed', 'current', cw.closed FROM current_week cw
            UNION ALL SELECT 'closed', 'previous', lw.closed FROM last_week lw
            UNION ALL SELECT 'stale', 'current', cw.stale FROM current_week cw
            UNION ALL SELECT 'stale', 'previous', lw.stale FROM last_week lw
            UNION ALL SELECT 'escalated', 'current', cw.escalated FROM current_week cw
            UNION ALL SELECT 'escalated', 'previous', lw.escalated FROM last_week lw
            """)
                .fetch(r -> new WeeklyActivityMetric(
                        r.get("type", String.class),
                        r.get("week", String.class),
                        r.get("count", Long.class)
                ));
    }

    @Override
    // taken from support-ui
    public List<ResolutionTimeByTagMetric> getResolutionTimeByTag() {
        return dsl.resultQuery("""
            WITH tag_durations AS (
                SELECT
                    unnest(td.tags) AS tag,
                    EXTRACT(EPOCH FROM business_time_between(
                        td.first_open_ts,
                        td.last_closed_ts,
                        'Europe/London'
                    )) AS duration
                FROM aggregated_ticket_data td
                WHERE td.status = 'closed'
                  AND td.last_closed_ts IS NOT NULL
                  AND td.first_open_ts IS NOT NULL
            )
            SELECT
                tag,
                percentile_cont(0.5) WITHIN GROUP (ORDER BY duration) AS p50,
                percentile_cont(0.9) WITHIN GROUP (ORDER BY duration) AS p90
            FROM tag_durations
            WHERE duration IS NOT NULL AND duration > 0
            GROUP BY tag
            HAVING COUNT(*) > 0
            ORDER BY p50 DESC
            """)
                .fetch(r -> new ResolutionTimeByTagMetric(
                        r.get("tag", String.class),
                        r.get("p50", Double.class) != null ? r.get("p50", Double.class) : 0.0,
                        r.get("p90", Double.class) != null ? r.get("p90", Double.class) : 0.0
                ));
    }
}
