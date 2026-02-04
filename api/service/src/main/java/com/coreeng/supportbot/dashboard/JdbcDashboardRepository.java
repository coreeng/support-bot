package com.coreeng.supportbot.dashboard;

import com.coreeng.supportbot.dashboard.rest.EscalationAvgDurationByTag;
import com.coreeng.supportbot.dashboard.rest.EscalationByImpact;
import com.coreeng.supportbot.dashboard.rest.EscalationByTeam;
import com.coreeng.supportbot.dashboard.rest.EscalationCountByTag;
import com.coreeng.supportbot.dashboard.rest.EscalationCountTrend;
import com.coreeng.supportbot.dashboard.rest.WeeklyComparison;
import com.coreeng.supportbot.dashboard.rest.WeeklyTopEscalatedTag;
import com.coreeng.supportbot.dashboard.rest.WeeklyTicketCounts;
import com.coreeng.supportbot.dashboard.rest.ResolutionDurationBucket;
import com.coreeng.supportbot.dashboard.rest.ResolutionPercentiles;
import com.coreeng.supportbot.dashboard.rest.ResponsePercentiles;
import com.coreeng.supportbot.dashboard.rest.ResolutionIncomingResolvedRate;
import com.coreeng.supportbot.dashboard.rest.ResolutionOpenTicketAges;
import com.coreeng.supportbot.dashboard.rest.ResolutionTimeByTag;
import com.coreeng.supportbot.dashboard.rest.ResolutionWeeklyPercentiles;
import com.coreeng.supportbot.dashboard.rest.ResponseUnattendedCount;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class JdbcDashboardRepository implements DashboardRepository {
    private final DSLContext dsl;

    @Override
    public List<Integer> getResponseDistribution(String dateFrom, String dateTo) {
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
            AND ({0}::date IS NULL OR query_posted_ts::date >= {0}::date)
            AND ({1}::date IS NULL OR query_posted_ts::date <= {1}::date)
          ORDER BY ticket_id
          """, DSL.val(dateFrom), DSL.val(dateTo))
                .fetch(r -> {
                    Double d = r.get("duration", Double.class);
                    return d != null ? d.intValue() : 0;
                })
                .stream()
                .filter(d -> d > 0)
                .toList();
    }

    @Override
    public ResponsePercentiles getResponsePercentiles(String dateFrom, String dateTo) {
        var result = dsl.resultQuery("""
              WITH response_durations AS (
                  SELECT EXTRACT(EPOCH FROM business_time_between(
                      query_posted_ts::timestamptz,
                      first_open_ts::timestamptz,
                      'Europe/London'
                  )) AS duration
                  FROM aggregated_ticket_data
                  WHERE first_open_ts IS NOT NULL
                    AND query_posted_ts IS NOT NULL
                    AND first_open_ts::timestamptz > query_posted_ts::timestamptz
                    AND ({0}::date IS NULL OR first_open_ts::date >= {0}::date)
                    AND ({1}::date IS NULL OR first_open_ts::date <= {1}::date)
              )
              SELECT
                  percentile_cont(0.5) WITHIN GROUP (ORDER BY duration) AS p50,
                  percentile_cont(0.9) WITHIN GROUP (ORDER BY duration) AS p90
              FROM response_durations
              WHERE duration IS NOT NULL AND duration > 0
              """, DSL.val(dateFrom), DSL.val(dateTo)).fetchOne();

        if (result == null) {
            return new ResponsePercentiles(0.0, 0.0);
        }
        return new ResponsePercentiles(
                result.get("p50", Double.class) != null ? result.get("p50", Double.class) : 0.0,
                result.get("p90", Double.class) != null ? result.get("p90", Double.class) : 0.0
        );
    }

    @Override
    public ResolutionPercentiles getResolutionPercentiles(String dateFrom, String dateTo) {
        var result = dsl.resultQuery("""                                                                                                                                                                                    
          SELECT  
              percentile_cont(0.5) WITHIN GROUP (ORDER BY duration) AS p50,
              percentile_cont(0.75) WITHIN GROUP (ORDER BY duration) AS p75,
              percentile_cont(0.9) WITHIN GROUP (ORDER BY duration) AS p90
          FROM (
              SELECT
                  EXTRACT(EPOCH FROM business_time_between(
                      first_open_ts::timestamptz,
                      last_closed_ts::timestamptz,
                      'Europe/London'
                  )) AS duration
              FROM aggregated_ticket_data
              WHERE last_closed_ts IS NOT NULL
                AND first_open_ts IS NOT NULL
                AND last_closed_ts::timestamptz > first_open_ts::timestamptz
                AND ({0}::date IS NULL OR first_open_ts::date >= {0}::date)
                AND ({1}::date IS NULL OR first_open_ts::date <= {1}::date)
          ) sub
          WHERE duration IS NOT NULL AND duration > 0
          """, DSL.val(dateFrom), DSL.val(dateTo)).fetchOne();

        if (result == null) {
            return new ResolutionPercentiles(0.0, 0.0, 0.0);
        }
        return new ResolutionPercentiles(
                result.get("p50", Double.class) != null ? result.get("p50", Double.class) : 0.0,
                result.get("p75", Double.class) != null ? result.get("p75", Double.class) : 0.0,
                result.get("p90", Double.class) != null ? result.get("p90", Double.class) : 0.0
        );
    }

    @Override
    public ResponseUnattendedCount getResponseUnattendedCount(String dateFrom, String dateTo) {
        var result = dsl.resultQuery("""
          SELECT COUNT(*) AS count
          FROM aggregated_ticket_data
          WHERE ticket_id IS NULL
            AND ({0}::date IS NULL OR query_posted_ts::date >= {0}::date)
            AND ({1}::date IS NULL OR query_posted_ts::date <= {1}::date)
          """, DSL.val(dateFrom), DSL.val(dateTo)).fetchOne();

        if (result == null) {
            return new ResponseUnattendedCount(0);
        }
        return new ResponseUnattendedCount(result.get("count", Long.class));
    }

    @Override
    public List<ResolutionDurationBucket> getResolutionDurationDistribution(String dateFrom, String dateTo) {
        record BucketDef(String label, double minSeconds, double maxSeconds) {}
// TODO: Look at this implementation. UI requires this but I don't really like how it's been implemented.
        var bucketDefs = List.of(
                new BucketDef("< 15 min", 0, 900),
                new BucketDef("15-30 min", 900, 1800),
                new BucketDef("30-60 min", 1800, 3600),
                new BucketDef("1-2 hours", 3600, 7200),
                new BucketDef("2-4 hours", 7200, 14_400),
                new BucketDef("4-8 hours", 14_400, 28_800),
                new BucketDef("8-24 hours", 28_800, 86_400),
                new BucketDef("1-3 days", 86_400, 259_200),
                new BucketDef("3-7 days", 259_200, 604_800),
                new BucketDef("> 7 days", 604_800, Double.POSITIVE_INFINITY)
        );

        return dsl.resultQuery("""
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
                AND ({0}::date IS NULL OR first_open_ts::date >= {0}::date)
                AND ({1}::date IS NULL OR first_open_ts::date <= {1}::date)
          )
          SELECT
              bucket,
              COUNT(*) AS count
          FROM (
              SELECT width_bucket(duration_seconds, ARRAY[900,1800,3600,7200,14400,28800,86400,259200,604800])::int AS bucket
              FROM durations
              WHERE duration_seconds IS NOT NULL AND duration_seconds > 0
          ) b
          WHERE bucket IS NOT NULL AND bucket >= 0 AND bucket <= 10
          GROUP BY bucket
          ORDER BY bucket
          """, DSL.val(dateFrom), DSL.val(dateTo))
                .fetch(r -> {
                    int bucket = r.get("bucket", Integer.class);
                    int idx = Math.max(0, Math.min(bucket - 1, bucketDefs.size() - 1));
                    var def = bucketDefs.get(idx);
                    return new ResolutionDurationBucket(def.label(), r.get("count", Long.class), def.minSeconds(), def.maxSeconds());
                })
                .stream()
                .filter(b -> b.count() > 0)
                .toList();
    }

    @Override
    public List<ResolutionWeeklyPercentiles> getResolutionTimesByWeek(String dateFrom, String dateTo) {
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
                  AND ({0}::date IS NULL OR first_open_ts::date >= {0}::date)
                  AND ({1}::date IS NULL OR first_open_ts::date <= {1}::date)
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
            """, DSL.val(dateFrom), DSL.val(dateTo))
            .fetch(r -> new ResolutionWeeklyPercentiles(
                r.get("week", java.time.LocalDate.class).toString(),
                r.get("p50", Double.class) != null ? r.get("p50", Double.class) : 0.0,
                r.get("p75", Double.class) != null ? r.get("p75", Double.class) : 0.0,
                r.get("p90", Double.class) != null ? r.get("p90", Double.class) : 0.0
            ));
    }

    @Override
    public ResolutionOpenTicketAges getResolutionOpenTicketAges(String dateFrom, String dateTo) {
        var result = dsl.resultQuery("""
            WITH ages AS (
                SELECT EXTRACT(EPOCH FROM NOW() - first_open_ts::timestamptz) AS age_seconds
                FROM aggregated_ticket_data
                WHERE last_closed_ts IS NULL
                  AND first_open_ts IS NOT NULL
                  AND ({0}::date IS NULL OR first_open_ts::date >= {0}::date)
                  AND ({1}::date IS NULL OR first_open_ts::date <= {1}::date)
            )
            SELECT
                percentile_cont(0.5) WITHIN GROUP (ORDER BY age_seconds) AS p50,
                percentile_cont(0.9) WITHIN GROUP (ORDER BY age_seconds) AS p90
            FROM ages
            """, DSL.val(dateFrom), DSL.val(dateTo)).fetchOne();

        if (result == null) {
            return new ResolutionOpenTicketAges(0.0, 0.0);
        }
        return new ResolutionOpenTicketAges(
            result.get("p50", Double.class) != null ? result.get("p50", Double.class) : 0.0,
            result.get("p90", Double.class) != null ? result.get("p90", Double.class) : 0.0
        );
    }

    @Override
    public List<ResolutionTimeByTag> getResolutionTimeByTag(String dateFrom, String dateTo) {
        return dsl.resultQuery("""
            WITH tag_durations AS (
                SELECT
                    unnest(tags) AS tag,
                    EXTRACT(EPOCH FROM business_time_between(
                        first_open_ts,
                        last_closed_ts,
                        'Europe/London'
                    )) AS duration
                FROM aggregated_ticket_data
                WHERE status = 'closed'
                  AND last_closed_ts IS NOT NULL
                  AND first_open_ts IS NOT NULL
                  AND ({0}::date IS NULL OR first_open_ts::date >= {0}::date)
                  AND ({1}::date IS NULL OR first_open_ts::date <= {1}::date)
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
            LIMIT 15
            """, DSL.val(dateFrom), DSL.val(dateTo))
            .fetch(r -> new ResolutionTimeByTag(
                r.get("tag", String.class),
                r.get("p50", Double.class) != null ? r.get("p50", Double.class) : 0.0,
                r.get("p90", Double.class) != null ? r.get("p90", Double.class) : 0.0
            ))
            .stream()
            .filter(t -> t.p50() > 0 && t.p90() > 0)
            .toList();
    }

    @Override
    public List<ResolutionIncomingResolvedRate> getResolutionIncomingResolvedRate(String dateFrom, String dateTo) {
        // TODO: implement left for last due to complexity
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public List<EscalationAvgDurationByTag> getEscalationAvgDurationByTag(String dateFrom, String dateTo) {
        return dsl.resultQuery("""
            SELECT
                unnest(ed.tags) AS tag,
                AVG(EXTRACT(EPOCH FROM business_time_between(
                    ed.open_ts,
                    ed.resolved_ts,
                    'Europe/London'
                ))) AS avg_duration
            FROM aggregated_escalation_data ed
            WHERE ed.open_ts IS NOT NULL
              AND ed.resolved_ts IS NOT NULL
              AND ed.tags IS NOT NULL
              AND ({0}::date IS NULL OR ed.open_ts::date >= {0}::date)
              AND ({1}::date IS NULL OR ed.open_ts::date <= {1}::date)
            GROUP BY tag
            HAVING AVG(EXTRACT(EPOCH FROM business_time_between(ed.open_ts, ed.resolved_ts, 'Europe/London'))) > 0
            ORDER BY avg_duration DESC NULLS LAST
            LIMIT 15
            """, DSL.val(dateFrom), DSL.val(dateTo))
            .fetch(r -> new EscalationAvgDurationByTag(
                r.get("tag", String.class),
                r.get("avg_duration", Double.class) != null ? r.get("avg_duration", Double.class) : 0.0
            ))
            .stream()
            .filter(e -> e.avgDurationSeconds() > 0)
            .toList();
    }

    @Override
    public List<EscalationCountByTag> getEscalationCountByTag(String dateFrom, String dateTo) {
        return dsl.resultQuery("""
            SELECT
                unnest(ed.tags) AS tag,
                COUNT(*) AS count
            FROM aggregated_escalation_data ed
            WHERE ed.open_ts IS NOT NULL
              AND ed.tags IS NOT NULL
              AND ({0}::date IS NULL OR ed.open_ts::date >= {0}::date)
              AND ({1}::date IS NULL OR ed.open_ts::date <= {1}::date)
            GROUP BY tag
            HAVING COUNT(*) > 0
            ORDER BY count DESC
            LIMIT 15
            """, DSL.val(dateFrom), DSL.val(dateTo))
            .fetch(r -> new EscalationCountByTag(
                r.get("tag", String.class),
                r.get("count", Long.class)
            ));
    }

    @Override
    public List<EscalationCountTrend> getEscalationCountTrend(String dateFrom, String dateTo) {
        return dsl.resultQuery("""
            SELECT
                DATE_TRUNC('day', ed.open_ts::timestamptz) AS escalation_date,
                COUNT(ed.escalation_id) AS escalations
            FROM aggregated_escalation_data ed
            WHERE ed.open_ts IS NOT NULL
              AND ({0}::date IS NULL OR ed.open_ts::date >= {0}::date)
              AND ({1}::date IS NULL OR ed.open_ts::date <= {1}::date)
            GROUP BY escalation_date
            ORDER BY escalation_date
            """, DSL.val(dateFrom), DSL.val(dateTo))
            .fetch(r -> new EscalationCountTrend(
                r.get("escalation_date", java.time.LocalDate.class).toString(),
                r.get("escalations", Long.class)
            ));
    }

    @Override
    public List<EscalationByTeam> getEscalationByTeam(String dateFrom, String dateTo) {
        return dsl.resultQuery("""
            SELECT
                COALESCE(ed.team_id, 'Unassigned') AS team_name,
                COUNT(ed.escalation_id) AS total_escalations
            FROM aggregated_escalation_data ed
            INNER JOIN aggregated_ticket_data td ON ed.ticket_id = td.ticket_id
            WHERE ed.open_ts IS NOT NULL
              AND ({0}::date IS NULL OR td.first_open_ts::date >= {0}::date)
              AND ({1}::date IS NULL OR td.first_open_ts::date <= {1}::date)
            GROUP BY ed.team_id
            HAVING COUNT(ed.escalation_id) > 0
            ORDER BY total_escalations DESC
            LIMIT 10
            """, DSL.val(dateFrom), DSL.val(dateTo))
            .fetch(r -> new EscalationByTeam(
                r.get("team_name", String.class),
                r.get("total_escalations", Long.class)
            ));
    }

    @Override
    public List<EscalationByImpact> getEscalationByImpact(String dateFrom, String dateTo) {
        return dsl.resultQuery("""
            SELECT
                COALESCE(td.impact, 'Not yet tagged') AS impact_level,
                COUNT(ed.escalation_id) AS total_escalations
            FROM aggregated_escalation_data ed
            INNER JOIN aggregated_ticket_data td ON ed.ticket_id = td.ticket_id
            WHERE ed.open_ts IS NOT NULL
              AND ({0}::date IS NULL OR ed.open_ts::date >= {0}::date)
              AND ({1}::date IS NULL OR ed.open_ts::date <= {1}::date)
            GROUP BY td.impact
            HAVING COUNT(ed.escalation_id) > 0
            ORDER BY total_escalations DESC
            """, DSL.val(dateFrom), DSL.val(dateTo))
            .fetch(r -> new EscalationByImpact(
                r.get("impact_level", String.class),
                r.get("total_escalations", Long.class)
            ));
    }

    @Override
    public List<WeeklyTicketCounts> getWeeklyTicketCounts(String dateFrom, String dateTo) {
        return dsl.resultQuery("""
            WITH weeks AS (
                SELECT DISTINCT DATE_TRUNC('week', first_open_ts::timestamptz) AS week
                FROM aggregated_ticket_data
                WHERE first_open_ts IS NOT NULL
                  AND ({0}::date IS NULL OR first_open_ts::date >= {0}::date)
                  AND ({1}::date IS NULL OR first_open_ts::date <= {1}::date)
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
            """, DSL.val(dateFrom), DSL.val(dateTo))
            .fetch(r -> new WeeklyTicketCounts(
                r.get("week", java.time.LocalDate.class).toString(),
                r.get("opened", Long.class),
                r.get("closed", Long.class),
                r.get("escalated", Long.class),
                r.get("stale", Long.class)
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
            SELECT 'closed', cw.closed, lw.closed FROM current_week cw, last_week lw
            UNION ALL
            SELECT 'stale', cw.stale, lw.stale FROM current_week cw, last_week lw
            UNION ALL
            SELECT 'escalated', cw.escalated, lw.escalated FROM current_week cw, last_week lw
            """)
            .fetch(r -> {
                long thisWeek = r.get("this_week", Long.class);
                long lastWeek = r.get("last_week", Long.class);
                return new WeeklyComparison(
                    r.get("metric", String.class),
                    thisWeek,
                    lastWeek,
                    thisWeek - lastWeek
                );
            });
    }

    @Override
    public List<WeeklyTopEscalatedTag> getWeeklyTopEscalatedTags() {
        return dsl.resultQuery("""
            SELECT
                unnest(ed.tags) AS tag,
                COUNT(*) AS count
            FROM aggregated_escalation_data ed
            INNER JOIN aggregated_ticket_data td ON ed.ticket_id = td.ticket_id
            WHERE DATE_TRUNC('week', ed.open_ts::timestamptz) = DATE_TRUNC('week', now())
              AND ed.tags IS NOT NULL
            GROUP BY tag
            HAVING COUNT(*) > 0
            ORDER BY count DESC
            LIMIT 10
            """)
            .fetch(r -> new WeeklyTopEscalatedTag(
                r.get("tag", String.class),
                r.get("count", Long.class)
            ));
    }
}
