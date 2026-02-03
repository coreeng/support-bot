package com.coreeng.supportbot.dashboard;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * JDBC implementation of DashboardRepository using JOOQ for raw SQL queries.
 * All queries use the aggregated_ticket_data and aggregated_escalation_data views.
 */
@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JdbcDashboardRepository implements DashboardRepository {

    private final DSLContext dsl;
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    // ===== Response SLAs =====

    @Override
    public List<Double> getFirstResponseDurationDistribution(LocalDate dateFrom, LocalDate dateTo) {
        String dateFilter = buildDateFilter(dateFrom, dateTo, "query_posted_ts");

        return dsl.resultQuery("""
            SELECT
                EXTRACT(EPOCH FROM business_time_between(
                    query_posted_ts,
                    first_open_ts,
                    'Europe/London'
                )) AS duration
            FROM aggregated_ticket_data
            WHERE first_open_ts IS NOT NULL
              AND query_posted_ts IS NOT NULL
              AND first_open_ts > query_posted_ts
              """ + dateFilter + """
            ORDER BY ticket_id
            """)
            .fetch(r -> {
                Double d = r.get("duration", Double.class);
                return d != null && d > 0 ? d : null;
            })
            .stream()
            .filter(d -> d != null)
            .toList();
    }

    @Override
    public ResponsePercentiles getFirstResponsePercentiles(LocalDate dateFrom, LocalDate dateTo) {
        String dateFilter = buildDateFilter(dateFrom, dateTo, "first_open_ts");

        var result = dsl.resultQuery("""
            WITH response_durations AS (
                SELECT
                    EXTRACT(EPOCH FROM business_time_between(
                        query_posted_ts::timestamptz,
                        first_open_ts::timestamptz,
                        'Europe/London'
                    )) AS duration
                FROM aggregated_ticket_data
                WHERE first_open_ts IS NOT NULL
                  AND query_posted_ts IS NOT NULL
                  AND first_open_ts::timestamptz > query_posted_ts::timestamptz
                  """ + dateFilter + """
            )
            SELECT
                percentile_cont(0.5) WITHIN GROUP (ORDER BY duration) AS p50,
                percentile_cont(0.9) WITHIN GROUP (ORDER BY duration) AS p90
            FROM response_durations
            WHERE duration IS NOT NULL AND duration > 0
            """).fetchOne();

        if (result == null) {
            return new ResponsePercentiles(0.0, 0.0);
        }
        return new ResponsePercentiles(
            nullToZero(result.get("p50", Double.class)),
            nullToZero(result.get("p90", Double.class))
        );
    }

    @Override
    public long getUnattendedQueriesCount(LocalDate dateFrom, LocalDate dateTo) {
        String dateFilter = buildDateFilter(dateFrom, dateTo, "query_posted_ts");

        var result = dsl.resultQuery("""
            SELECT COUNT(*) AS count
            FROM aggregated_ticket_data
            WHERE ticket_id IS NULL
              """ + dateFilter + """
            """).fetchOne();

        return result != null ? nullToZero(result.get("count", Long.class)) : 0;
    }

    // ===== Resolution SLAs =====

    @Override
    public ResolutionPercentiles getResolutionPercentiles(LocalDate dateFrom, LocalDate dateTo) {
        String dateFilter = buildDateFilter(dateFrom, dateTo, "first_open_ts");

        var result = dsl.resultQuery("""
            SELECT
                percentile_cont(0.5) WITHIN GROUP (ORDER BY duration) AS p50,
                percentile_cont(0.75) WITHIN GROUP (ORDER BY duration) AS p75,
                percentile_cont(0.9) WITHIN GROUP (ORDER BY duration) AS p90
            FROM (
                SELECT
                    ticket_id,
                    EXTRACT(EPOCH FROM business_time_between(
                        first_open_ts::timestamptz,
                        last_closed_ts::timestamptz,
                        'Europe/London'
                    )) AS duration
                FROM aggregated_ticket_data
                WHERE last_closed_ts IS NOT NULL
                  AND first_open_ts IS NOT NULL
                  AND last_closed_ts::timestamptz > first_open_ts::timestamptz
                  """ + dateFilter + """
            ) sub
            WHERE duration IS NOT NULL AND duration > 0
            """).fetchOne();

        if (result == null) {
            return new ResolutionPercentiles(0.0, 0.0, 0.0);
        }
        return new ResolutionPercentiles(
            nullToZero(result.get("p50", Double.class)),
            nullToZero(result.get("p75", Double.class)),
            nullToZero(result.get("p90", Double.class))
        );
    }

    @Override
    public List<ResolutionDurationBucket> getResolutionDurationDistribution(LocalDate dateFrom, LocalDate dateTo) {
        String dateFilter = buildDateFilter(dateFrom, dateTo, "first_open_ts");

        var rows = dsl.resultQuery("""
            WITH durations AS (
                SELECT
                    EXTRACT(EPOCH FROM business_time_between(
                        first_open_ts,
                        last_closed_ts,
                        'Europe/London'
                    )) AS duration_seconds
                FROM aggregated_ticket_data
                WHERE last_closed_ts IS NOT NULL
                  AND first_open_ts IS NOT NULL
                  AND last_closed_ts > first_open_ts
                  """ + dateFilter + """
            )
            SELECT
                bucket,
                COUNT(*) AS count
            FROM (
                SELECT width_bucket(duration_seconds, ARRAY[
                    900,       -- 15m
                    1800,      -- 30m
                    3600,      -- 1h
                    7200,      -- 2h
                    14400,     -- 4h
                    28800,     -- 8h
                    86400,     -- 24h
                    259200,    -- 3d
                    604800     -- 7d
                ])::int AS bucket
                FROM durations
                WHERE duration_seconds IS NOT NULL AND duration_seconds > 0
            ) b
            WHERE bucket IS NOT NULL AND bucket >= 0 AND bucket <= 10
            GROUP BY bucket
            ORDER BY bucket
            """).fetch();

        // Bucket definitions matching the UI
        record BucketDef(String label, double minSeconds, double maxSeconds) {}
        var bucketDefs = List.of(
            new BucketDef("< 15 min", 0, 900),
            new BucketDef("15-30 min", 900, 1800),
            new BucketDef("30-60 min", 1800, 3600),
            new BucketDef("1-2 hours", 3600, 7200),
            new BucketDef("2-4 hours", 7200, 14400),
            new BucketDef("4-8 hours", 14400, 28800),
            new BucketDef("8-24 hours", 28800, 86400),
            new BucketDef("1-3 days", 86400, 259200),
            new BucketDef("3-7 days", 259200, 604800),
            new BucketDef("> 7 days", 604800, Double.POSITIVE_INFINITY)
        );

        List<ResolutionDurationBucket> result = new ArrayList<>();
        for (var row : rows) {
            Integer bucket = row.get("bucket", Integer.class);
            Long count = row.get("count", Long.class);
            if (bucket != null && count != null && count > 0) {
                int idx = Math.max(0, Math.min(bucket - 1, bucketDefs.size() - 1));
                var def = bucketDefs.get(idx);
                result.add(new ResolutionDurationBucket(
                    def.label,
                    count,
                    def.minSeconds / 60.0,
                    def.maxSeconds == Double.POSITIVE_INFINITY ? Double.POSITIVE_INFINITY : def.maxSeconds / 60.0
                ));
            }
        }
        return result;
    }

    @Override
    public List<WeeklyResolutionTimes> getResolutionTimesByWeek(LocalDate dateFrom, LocalDate dateTo) {
        String dateFilter = buildDateFilter(dateFrom, dateTo, "first_open_ts");

        return dsl.resultQuery("""
            WITH ticket_durations AS (
                SELECT
                    date_trunc('week', first_open_ts::timestamptz) AS week,
                    EXTRACT(EPOCH FROM business_time_between(
                        first_open_ts::timestamptz,
                        last_closed_ts::timestamptz,
                        'Europe/London'
                    )) AS duration
                FROM aggregated_ticket_data
                WHERE last_closed_ts IS NOT NULL
                  AND first_open_ts IS NOT NULL
                  AND last_closed_ts::timestamptz > first_open_ts::timestamptz
                  """ + dateFilter + """
            )
            SELECT
                week,
                percentile_cont(0.5) WITHIN GROUP (ORDER BY duration) AS p50,
                percentile_cont(0.75) WITHIN GROUP (ORDER BY duration) AS p75,
                percentile_cont(0.9) WITHIN GROUP (ORDER BY duration) AS p90
            FROM ticket_durations
            WHERE duration IS NOT NULL AND duration > 0
            GROUP BY week
            ORDER BY week
            """)
            .fetch(r -> new WeeklyResolutionTimes(
                r.get("week", java.sql.Timestamp.class).toLocalDateTime().toLocalDate().format(ISO_DATE),
                nullToZero(r.get("p50", Double.class)),
                nullToZero(r.get("p75", Double.class)),
                nullToZero(r.get("p90", Double.class))
            ));
    }

    @Override
    public UnresolvedTicketAges getUnresolvedTicketAges(LocalDate dateFrom, LocalDate dateTo) {
        String dateFilter = buildDateFilter(dateFrom, dateTo, "first_open_ts");

        var result = dsl.resultQuery("""
            WITH ages AS (
                SELECT NOW() - first_open_ts::timestamptz AS age
                FROM aggregated_ticket_data
                WHERE last_closed_ts IS NULL
                  AND first_open_ts IS NOT NULL
                  """ + dateFilter + """
            )
            SELECT
                percentile_cont(0.5) WITHIN GROUP (ORDER BY age)::text AS p50,
                percentile_cont(0.9) WITHIN GROUP (ORDER BY age)::text AS p90
            FROM ages
            """).fetchOne();

        if (result == null) {
            return new UnresolvedTicketAges("0 seconds", "0 seconds");
        }
        String p50 = result.get("p50", String.class);
        String p90 = result.get("p90", String.class);
        return new UnresolvedTicketAges(
            p50 != null ? p50 : "0 seconds",
            p90 != null ? p90 : "0 seconds"
        );
    }

    @Override
    public List<IncomingVsResolved> getIncomingVsResolvedRate(LocalDate dateFrom, LocalDate dateTo) {
        // Default to last 7 days if no date range
        LocalDate start = dateFrom != null ? dateFrom : LocalDate.now().minusDays(7);
        LocalDate end = dateTo != null ? dateTo : LocalDate.now();

        long diffDays = java.time.temporal.ChronoUnit.DAYS.between(start, end);
        boolean useDailyInterval = diffDays > 60;

        String truncFunc = useDailyInterval
            ? "date_trunc('day', query_posted_ts::timestamptz)"
            : "date_trunc('hour', query_posted_ts::timestamptz)";
        String truncFuncResolved = useDailyInterval
            ? "date_trunc('day', last_closed_ts::timestamptz)"
            : "date_trunc('hour', last_closed_ts::timestamptz)";
        String interval = useDailyInterval ? "'1 day'::interval" : "'1 hour'::interval";

        String startStr = start.format(ISO_DATE);
        String endStr = end.format(ISO_DATE);

        return dsl.resultQuery("""
            WITH time_series AS (
                SELECT generate_series(
                    '""" + startStr + """'::timestamptz,
                    '""" + endStr + """'::timestamptz + INTERVAL '1 day',
                    """ + interval + """
                ) AS time_bucket
            ),
            incoming_counts AS (
                SELECT
                    """ + truncFunc + """ AS time_bucket,
                    COUNT(DISTINCT query_id) AS count
                FROM aggregated_ticket_data
                WHERE query_posted_ts IS NOT NULL
                  AND query_posted_ts::date >= '""" + startStr + """'::date
                  AND query_posted_ts::date <= '""" + endStr + """'::date
                GROUP BY """ + truncFunc + """
            ),
            resolved_counts AS (
                SELECT
                    """ + truncFuncResolved + """ AS time_bucket,
                    COUNT(*) AS count
                FROM aggregated_ticket_data
                WHERE last_closed_ts IS NOT NULL
                  AND last_closed_ts::date >= '""" + startStr + """'::date
                  AND last_closed_ts::date <= '""" + endStr + """'::date
                GROUP BY """ + truncFuncResolved + """
            )
            SELECT
                ts.time_bucket,
                COALESCE(ic.count, 0) AS incoming,
                COALESCE(rc.count, 0) AS resolved
            FROM time_series ts
            LEFT JOIN incoming_counts ic ON ts.time_bucket = ic.time_bucket
            LEFT JOIN resolved_counts rc ON ts.time_bucket = rc.time_bucket
            WHERE ts.time_bucket >= '""" + startStr + """'::timestamptz
              AND ts.time_bucket <= '""" + endStr + """'::timestamptz + INTERVAL '1 day'
            ORDER BY ts.time_bucket
            """)
            .fetch(r -> new IncomingVsResolved(
                r.get("time_bucket", java.sql.Timestamp.class).toInstant().toString(),
                nullToZero(r.get("incoming", Long.class)),
                nullToZero(r.get("resolved", Long.class))
            ));
    }

    // ===== Escalation SLAs =====

    @Override
    public List<TagDuration> getAvgEscalationDurationByTag(LocalDate dateFrom, LocalDate dateTo) {
        String dateFilter = buildEscalationDateFilter(dateFrom, dateTo, "ed.open_ts");

        return dsl.resultQuery("""
            SELECT
                unnest(ed.tags) AS tag,
                AVG(EXTRACT(EPOCH FROM business_time_between(
                    ed.open_ts,
                    ed.resolved_ts,
                    'Europe/London'
                ))) / 3600 AS avg_duration
            FROM aggregated_escalation_data ed
            WHERE ed.open_ts IS NOT NULL
              AND ed.resolved_ts IS NOT NULL
              AND ed.tags IS NOT NULL
              """ + dateFilter + """
            GROUP BY tag
            HAVING AVG(EXTRACT(EPOCH FROM business_time_between(ed.open_ts, ed.resolved_ts, 'Europe/London'))) > 0
            ORDER BY avg_duration DESC NULLS LAST
            LIMIT 15
            """)
            .fetch(r -> new TagDuration(
                r.get("tag", String.class),
                nullToZero(r.get("avg_duration", Double.class))
            ))
            .stream()
            .filter(td -> td.avgDuration() > 0)
            .toList();
    }

    @Override
    public List<TagCount> getEscalationPercentageByTag(LocalDate dateFrom, LocalDate dateTo) {
        String dateFilter = buildEscalationDateFilter(dateFrom, dateTo, "ed.open_ts");

        return dsl.resultQuery("""
            SELECT
                unnest(ed.tags) AS tag,
                COUNT(*) AS count
            FROM aggregated_escalation_data ed
            WHERE ed.open_ts IS NOT NULL
              AND ed.tags IS NOT NULL
              """ + dateFilter + """
            GROUP BY tag
            HAVING COUNT(*) > 0
            ORDER BY count DESC
            LIMIT 15
            """)
            .fetch(r -> new TagCount(
                r.get("tag", String.class),
                nullToZero(r.get("count", Long.class))
            ))
            .stream()
            .filter(tc -> tc.count() > 0)
            .toList();
    }

    @Override
    public List<DateEscalations> getEscalationTrendsByDate(LocalDate dateFrom, LocalDate dateTo) {
        String dateFilter = buildEscalationDateFilter(dateFrom, dateTo, "ed.open_ts");

        return dsl.resultQuery("""
            SELECT
                DATE_TRUNC('day', ed.open_ts::timestamptz) AS escalation_date,
                COUNT(ed.escalation_id) AS escalations
            FROM aggregated_escalation_data ed
            WHERE ed.open_ts IS NOT NULL
              """ + dateFilter + """
            GROUP BY escalation_date
            ORDER BY escalation_date
            """)
            .fetch(r -> new DateEscalations(
                r.get("escalation_date", java.sql.Timestamp.class).toLocalDateTime().toLocalDate().format(ISO_DATE),
                nullToZero(r.get("escalations", Long.class))
            ));
    }

    @Override
    public List<TeamEscalations> getEscalationsByTeam(LocalDate dateFrom, LocalDate dateTo) {
        String dateFilter = buildDateFilter(dateFrom, dateTo, "td.first_open_ts");

        return dsl.resultQuery("""
            SELECT
                COALESCE(ed.team_id, 'Unassigned') AS team_name,
                COUNT(ed.escalation_id) AS total_escalations
            FROM aggregated_escalation_data ed
            INNER JOIN aggregated_ticket_data td ON ed.ticket_id = td.ticket_id
            WHERE ed.open_ts IS NOT NULL
              """ + dateFilter + """
            GROUP BY ed.team_id
            HAVING COUNT(ed.escalation_id) > 0
            ORDER BY total_escalations DESC
            LIMIT 10
            """)
            .fetch(r -> new TeamEscalations(
                r.get("team_name", String.class),
                nullToZero(r.get("total_escalations", Long.class))
            ))
            .stream()
            .filter(te -> te.totalEscalations() > 0)
            .toList();
    }

    @Override
    public List<ImpactEscalations> getEscalationsByImpact(LocalDate dateFrom, LocalDate dateTo) {
        String dateFilter = buildEscalationDateFilter(dateFrom, dateTo, "ed.open_ts");

        return dsl.resultQuery("""
            SELECT
                COALESCE(td.impact, 'Not yet tagged') AS impact_level,
                COUNT(ed.escalation_id) AS total_escalations
            FROM aggregated_escalation_data ed
            INNER JOIN aggregated_ticket_data td ON ed.ticket_id = td.ticket_id
            WHERE ed.open_ts IS NOT NULL
              """ + dateFilter + """
            GROUP BY td.impact
            HAVING COUNT(ed.escalation_id) > 0
            ORDER BY total_escalations DESC
            """)
            .fetch(r -> new ImpactEscalations(
                r.get("impact_level", String.class),
                nullToZero(r.get("total_escalations", Long.class))
            ))
            .stream()
            .filter(ie -> ie.totalEscalations() > 0)
            .toList();
    }

    // ===== Weekly Trends =====

    @Override
    public List<WeeklyTicketCounts> getWeeklyTicketCounts() {
        return dsl.resultQuery("""
            WITH weeks AS (
                SELECT DISTINCT DATE_TRUNC('week', first_open_ts::timestamptz) AS week
                FROM aggregated_ticket_data
                WHERE first_open_ts IS NOT NULL
            )
            SELECT
                w.week,
                COUNT(DISTINCT CASE WHEN DATE_TRUNC('week', td.first_open_ts::timestamptz) = w.week THEN td.ticket_id END) AS opened,
                COUNT(DISTINCT CASE WHEN DATE_TRUNC('week', td.last_closed_ts::timestamptz) = w.week THEN td.ticket_id END) AS closed,
                COUNT(DISTINCT CASE WHEN DATE_TRUNC('week', ed.open_ts::timestamptz) = w.week THEN ed.escalation_id END) AS escalated,
                COUNT(DISTINCT CASE WHEN td.status = 'stale' AND DATE_TRUNC('week', td.first_open_ts::timestamptz) = w.week THEN td.ticket_id END) AS stale
            FROM weeks w
            LEFT JOIN aggregated_ticket_data td ON 1=1
            LEFT JOIN aggregated_escalation_data ed ON td.ticket_id = ed.ticket_id
            GROUP BY w.week
            ORDER BY w.week
            """)
            .fetch(r -> new WeeklyTicketCounts(
                r.get("week", java.sql.Timestamp.class).toLocalDateTime().toLocalDate().format(ISO_DATE),
                nullToZero(r.get("opened", Long.class)),
                nullToZero(r.get("closed", Long.class)),
                nullToZero(r.get("escalated", Long.class)),
                nullToZero(r.get("stale", Long.class))
            ));
    }

    @Override
    public List<WeeklyComparison> getWeeklyComparison() {
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
            SELECT 'opened' AS metric, cw.opened AS this_week, lw.opened AS last_week FROM current_week cw, last_week lw
            UNION ALL
            SELECT 'closed' AS metric, cw.closed AS this_week, lw.closed AS last_week FROM current_week cw, last_week lw
            UNION ALL
            SELECT 'stale' AS metric, cw.stale AS this_week, lw.stale AS last_week FROM current_week cw, last_week lw
            UNION ALL
            SELECT 'escalated' AS metric, cw.escalated AS this_week, lw.escalated AS last_week FROM current_week cw, last_week lw
            """)
            .fetch(r -> {
                long thisWeek = nullToZero(r.get("this_week", Long.class));
                long lastWeek = nullToZero(r.get("last_week", Long.class));
                return new WeeklyComparison(
                    r.get("metric", String.class),
                    thisWeek,
                    lastWeek,
                    thisWeek - lastWeek
                );
            });
    }

    @Override
    public List<TagCount> getTopEscalatedTagsThisWeek() {
        return dsl.resultQuery("""
            SELECT
                unnest(ed.tags) AS tag,
                COUNT(*) AS count
            FROM aggregated_escalation_data ed
            INNER JOIN aggregated_ticket_data td ON ed.ticket_id = td.ticket_id
            WHERE DATE_TRUNC('week', ed.open_ts::timestamptz) = DATE_TRUNC('week', now())
              AND ed.tags IS NOT NULL
            GROUP BY tag
            ORDER BY count DESC
            LIMIT 10
            """)
            .fetch(r -> new TagCount(
                r.get("tag", String.class),
                nullToZero(r.get("count", Long.class))
            ))
            .stream()
            .filter(tc -> tc.count() > 0)
            .toList();
    }

    @Override
    public List<TagResolutionTime> getResolutionTimeByTag(LocalDate dateFrom, LocalDate dateTo) {
        String dateFilter = buildDateFilter(dateFrom, dateTo, "td.first_open_ts");

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
                  """ + dateFilter + """
            )
            SELECT
                tag,
                COUNT(*) AS ticket_count,
                percentile_cont(0.5) WITHIN GROUP (ORDER BY duration) AS p50,
                percentile_cont(0.9) WITHIN GROUP (ORDER BY duration) AS p90
            FROM tag_durations
            WHERE duration IS NOT NULL AND duration > 0
            GROUP BY tag
            HAVING COUNT(*) > 0
            ORDER BY p50 DESC
            LIMIT 15
            """)
            .fetch(r -> new TagResolutionTime(
                r.get("tag", String.class),
                nullToZero(r.get("p50", Double.class)),
                nullToZero(r.get("p90", Double.class))
            ))
            .stream()
            .filter(tr -> tr.p50() > 0 && tr.p90() > 0)
            .toList();
    }

    // ===== Helpers =====

    private String buildDateFilter(LocalDate dateFrom, LocalDate dateTo, String column) {
        if (dateFrom != null && dateTo != null) {
            return "AND " + column + "::date >= '" + dateFrom.format(ISO_DATE) + "'::date " +
                   "AND " + column + "::date <= '" + dateTo.format(ISO_DATE) + "'::date";
        }
        return "";
    }

    private String buildEscalationDateFilter(LocalDate dateFrom, LocalDate dateTo, String column) {
        if (dateFrom != null && dateTo != null) {
            return "AND " + column + "::date >= '" + dateFrom.format(ISO_DATE) + "'::date " +
                   "AND " + column + "::date <= '" + dateTo.format(ISO_DATE) + "'::date";
        }
        return "";
    }

    private static double nullToZero(Double value) {
        return value != null ? value : 0.0;
    }

    private static long nullToZero(Long value) {
        return value != null ? value : 0L;
    }
}
