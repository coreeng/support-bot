// src/db/dashboard/weekly-trends.ts
import prisma from '@/lib/prisma'
import { Prisma } from '@prisma/client'

/**
 * Get opened/closed/escalated/stale counts per week
 */
export async function getWeeklyTicketCounts() {
    try {
        const result = await prisma.$queryRaw<{
            week: Date
            opened: bigint
            closed: bigint
            escalated: bigint
            stale: bigint
        }[]>`
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
            ORDER BY w.week;
        `;
        
        return result.map(row => ({
            week: row.week.toISOString().split('T')[0],
            opened: Number(row.opened) || 0,
            closed: Number(row.closed) || 0,
            escalated: Number(row.escalated) || 0,
            stale: Number(row.stale) || 0
        }));
    } catch (error) {
        console.error('Error fetching weekly ticket counts:', error)
        throw error
    }
}

/**
 * Get current week vs last week comparison
 */
export async function getWeeklyComparison() {
    try {
        const result = await prisma.$queryRaw<{
            metric: string
            this_week: bigint
            last_week: bigint
        }[]>`
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
            SELECT 'escalated' AS metric, cw.escalated AS this_week, lw.escalated AS last_week FROM current_week cw, last_week lw;
        `;
        
        return result.map(row => ({
            label: row.metric,
            thisWeek: Number(row.this_week) || 0,
            lastWeek: Number(row.last_week) || 0,
            change: Number(row.this_week) - Number(row.last_week)
        }));
    } catch (error) {
        console.error('Error fetching weekly comparison:', error)
        throw error
    }
}

/**
 * Get top 10 tags escalated this week
 */
export async function getTopEscalatedTagsThisWeek() {
    try {
        const result = await prisma.$queryRaw<{
            tag: string
            count: bigint
        }[]>`
            SELECT 
                unnest(ed.tags) AS tag,
                COUNT(*) AS count
            FROM aggregated_escalation_data ed
            INNER JOIN aggregated_ticket_data td ON ed.ticket_id = td.ticket_id
            WHERE DATE_TRUNC('week', ed.open_ts::timestamptz) = DATE_TRUNC('week', now())
              AND ed.tags IS NOT NULL
            GROUP BY tag
            ORDER BY count DESC
            LIMIT 10;
        `;
        
        return result
            .map(row => ({
                tag: row.tag,
                count: Number(row.count) || 0
            }))
            .filter(item => item.count > 0); // Exclude tags with 0 escalations
    } catch (error) {
        console.error('Error fetching top escalated tags this week:', error)
        throw error
    }
}

/**
 * Get P50/P90 resolution time by tag
 * Limited to top 15 tags by P50 to keep charts readable
 */
export async function getResolutionTimeByTag(dateFrom?: string, dateTo?: string) {
    try {
        const dateFilter = dateFrom && dateTo
            ? Prisma.sql`AND td.first_open_ts::date >= ${dateFrom}::date 
                         AND td.first_open_ts::date <= ${dateTo}::date`
            : Prisma.empty
        
        const result = await prisma.$queryRaw<{
            tag: string
            p50: number
            p90: number
            ticket_count: bigint
        }[]>`
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
                  ${dateFilter}
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
            LIMIT 15;
        `;
        
        return result
            .map(row => ({
                tag: row.tag,
                p50: Number(row.p50) || 0,
                p90: Number(row.p90) || 0
            }))
            .filter(item => item.p50 > 0 && item.p90 > 0);
    } catch (error) {
        console.error('Error fetching resolution time by tag:', error)
        throw error
    }
}

