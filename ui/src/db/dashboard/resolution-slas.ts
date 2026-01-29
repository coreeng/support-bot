// src/db/dashboard/resolution-slas.ts
import prisma from '@/lib/prisma'
import { Prisma } from '@prisma/client'

/**
 * Get ticket resolution percentiles (P50, P75, P90)
 * Uses business_time_between function to calculate working hours
 */
export async function getTicketResolutionPercentiles(dateFrom?: string, dateTo?: string) {
    try {
        const dateFilter = dateFrom && dateTo
            ? Prisma.sql`AND first_open_ts::date >= ${dateFrom}::date 
                         AND first_open_ts::date <= ${dateTo}::date`
            : Prisma.empty
        
        const result = await prisma.$queryRaw<{
            p50: number
            p75: number
            p90: number
        }[]>`
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
                  ${dateFilter}
            ) sub
            WHERE duration IS NOT NULL AND duration > 0;
        `
        return result[0] || { p50: 0, p75: 0, p90: 0 }
    } catch (error) {
        console.error('Error fetching ticket resolution percentiles:', error)
        throw error
    }
}

/**
 * Get ticket resolution duration distribution (for histogram/chart)
 * Returns duration in seconds for each resolved ticket
 * Uses aggregated_ticket_data view only
 */
export async function getTicketResolutionDurationDistribution(dateFrom?: string, dateTo?: string) {
    try {
        const dateFilter = dateFrom && dateTo
            ? Prisma.sql`AND first_open_ts::date >= ${dateFrom}::date 
                         AND first_open_ts::date <= ${dateTo}::date`
            : Prisma.empty

        // Bucket boundaries in seconds (aligned with UI buckets)
        // <15m, 15-30m, 30-60m, 1-2h, 2-4h, 4-8h, 8-24h, 1-3d, 3-7d, >7d
        const bucketBounds = Prisma.sql`ARRAY[
            900,       -- 15m
            1800,      -- 30m
            3600,      -- 1h
            7200,      -- 2h
            14400,     -- 4h
            28800,     -- 8h
            86400,     -- 24h
            259200,    -- 3d
            604800     -- 7d
        ]`

        const rows = await prisma.$queryRaw<{
            bucket: number
            count: number
        }[]>`
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
                  ${dateFilter}
            )
            SELECT
                bucket,
                COUNT(*) AS count
            FROM (
                SELECT width_bucket(duration_seconds, ${bucketBounds})::int AS bucket
                FROM durations
                WHERE duration_seconds IS NOT NULL AND duration_seconds > 0
            ) b
            WHERE bucket IS NOT NULL AND bucket >= 0 AND bucket <= 10
            GROUP BY bucket
            ORDER BY bucket;
        `;

        // Map bucket index to labels and minute ranges for the UI TimeBucketChart
        const bucketDefinitions = [
            { label: '< 15 min', minSeconds: 0, maxSeconds: 900 },
            { label: '15-30 min', minSeconds: 900, maxSeconds: 1800 },
            { label: '30-60 min', minSeconds: 1800, maxSeconds: 3600 },
            { label: '1-2 hours', minSeconds: 3600, maxSeconds: 7200 },
            { label: '2-4 hours', minSeconds: 7200, maxSeconds: 14400 },
            { label: '4-8 hours', minSeconds: 14400, maxSeconds: 28800 },
            { label: '8-24 hours', minSeconds: 28800, maxSeconds: 86400 },
            { label: '1-3 days', minSeconds: 86400, maxSeconds: 259200 },
            { label: '3-7 days', minSeconds: 259200, maxSeconds: 604800 },
            { label: '> 7 days', minSeconds: 604800, maxSeconds: Number.POSITIVE_INFINITY },
        ]

        // width_bucket with N bounds returns values 0..N+1. We map only valid buckets.
        const mapped = rows.map(row => {
            if (row.bucket == null) return null
            const idx = Math.max(0, Math.min(row.bucket - 1, bucketDefinitions.length - 1))
            const def = bucketDefinitions[idx]
            return {
                label: def.label,
                count: Number(row.count) || 0,
                minMinutes: def.minSeconds / 60,
                maxMinutes: def.maxSeconds === Number.POSITIVE_INFINITY ? Number.POSITIVE_INFINITY : def.maxSeconds / 60,
            }
        }).filter((b): b is NonNullable<typeof b> => !!b && b.count > 0)

        return mapped;
    } catch (error) {
        console.error('Error fetching ticket resolution duration distribution:', error)
        throw error
    }
}

/**
 * Get resolution times by week (P50/P75/P90 per week)
 * Uses business_time_between function to calculate working hours
 */
export async function getResolutionTimesByWeek(dateFrom?: string, dateTo?: string) {
    try {
        const dateFilter = dateFrom && dateTo
            ? Prisma.sql`AND first_open_ts::date >= ${dateFrom}::date 
                         AND first_open_ts::date <= ${dateTo}::date`
            : Prisma.empty
        
        const result = await prisma.$queryRaw<{
            week: Date
            p50: number
            p75: number
            p90: number
        }[]>`
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
                  ${dateFilter}
            )
            SELECT 
                week,
                percentile_cont(0.5) WITHIN GROUP (ORDER BY duration) AS p50,
                percentile_cont(0.75) WITHIN GROUP (ORDER BY duration) AS p75,
                percentile_cont(0.9) WITHIN GROUP (ORDER BY duration) AS p90
            FROM ticket_durations
            WHERE duration IS NOT NULL AND duration > 0
            GROUP BY week
            ORDER BY week;
        `;
        
        return result.map(row => ({
            week: row.week.toISOString().split('T')[0],
            p50: Number(row.p50) || 0,
            p75: Number(row.p75) || 0,
            p90: Number(row.p90) || 0
        }));
    } catch (error) {
        console.error('Error fetching resolution times by week:', error)
        throw error
    }
}

/**
 * Get unresolved ticket ages (P50/P90)
 * Uses aggregated_ticket_data view only
 */
export async function getUnresolvedTicketAges(dateFrom?: string, dateTo?: string) {
    try {
        const dateFilter = dateFrom && dateTo
            ? Prisma.sql`AND first_open_ts::date >= ${dateFrom}::date 
                         AND first_open_ts::date <= ${dateTo}::date`
            : Prisma.empty
        
        const result = await prisma.$queryRaw<{
            p50: string
            p90: string
        }[]>`
            WITH ages AS (
                SELECT NOW() - first_open_ts::timestamptz AS age
                FROM aggregated_ticket_data
                WHERE last_closed_ts IS NULL
                  AND first_open_ts IS NOT NULL
                  ${dateFilter}
            )
            SELECT 
                percentile_cont(0.5) WITHIN GROUP (ORDER BY age)::text AS p50,
                percentile_cont(0.9) WITHIN GROUP (ORDER BY age)::text AS p90
            FROM ages;
        `;
        
        const row = result[0];
        return {
            p50: row?.p50 || '0 seconds',
            p90: row?.p90 || '0 seconds'
        };
    } catch (error) {
        console.error('Error fetching unresolved ticket ages:', error)
        throw error
    }
}

/**
 * Get incoming query rate and resolution rate over time
 * Returns hourly/daily counts of incoming queries and resolved tickets
 * Respects global date filter
 * Uses aggregated_ticket_data view only (includes unattended queries)
 */
export async function getIncomingVsResolvedRate(dateFrom?: string, dateTo?: string) {
    try {
        // Default to last 7 days if no date range provided
        const startDate = dateFrom || new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString().split('T')[0]
        const endDate = dateTo || new Date().toISOString().split('T')[0]

        // Use hourly for short ranges, daily for long ranges to keep payloads small
        const start = new Date(startDate)
        const end = new Date(endDate)
        const diffDays = Math.max(1, Math.ceil((end.getTime() - start.getTime()) / (1000 * 60 * 60 * 24)))
        const useDailyInterval = diffDays > 60
        
        const interval = useDailyInterval ? Prisma.sql`'1 day'::interval` : Prisma.sql`'1 hour'::interval`
        const truncFunc = useDailyInterval 
            ? Prisma.sql`date_trunc('day', query_posted_ts::timestamptz)` 
            : Prisma.sql`date_trunc('hour', query_posted_ts::timestamptz)`
        const truncFuncResolved = useDailyInterval 
            ? Prisma.sql`date_trunc('day', last_closed_ts::timestamptz)` 
            : Prisma.sql`date_trunc('hour', last_closed_ts::timestamptz)`
        
        const result = await prisma.$queryRaw<{
            time_bucket: Date
            incoming: bigint
            resolved: bigint
        }[]>`
            WITH time_series AS (
                SELECT generate_series(
                    ${startDate}::timestamptz,
                    ${endDate}::timestamptz + INTERVAL '1 day',
                    ${interval}
                ) AS time_bucket
            ),
            incoming_counts AS (
                SELECT 
                    ${truncFunc} AS time_bucket,
                    COUNT(DISTINCT query_id) AS count
                FROM aggregated_ticket_data
                WHERE query_posted_ts IS NOT NULL
                  AND query_posted_ts::date >= ${startDate}::date
                  AND query_posted_ts::date <= ${endDate}::date
                GROUP BY ${truncFunc}
            ),
            resolved_counts AS (
                SELECT 
                    ${truncFuncResolved} AS time_bucket,
                    COUNT(*) AS count
                FROM aggregated_ticket_data
                WHERE last_closed_ts IS NOT NULL
                  AND last_closed_ts::date >= ${startDate}::date
                  AND last_closed_ts::date <= ${endDate}::date
                GROUP BY ${truncFuncResolved}
            )
            SELECT 
                ts.time_bucket,
                COALESCE(ic.count, 0) AS incoming,
                COALESCE(rc.count, 0) AS resolved
            FROM time_series ts
            LEFT JOIN incoming_counts ic ON ts.time_bucket = ic.time_bucket
            LEFT JOIN resolved_counts rc ON ts.time_bucket = rc.time_bucket
            WHERE ts.time_bucket >= ${startDate}::timestamptz
              AND ts.time_bucket <= ${endDate}::timestamptz + INTERVAL '1 day'
            ORDER BY ts.time_bucket;
        `;
        
        const mappedResult = result.map(row => ({
            time: row.time_bucket.toISOString(),
            incoming: Number(row.incoming) || 0,
            resolved: Number(row.resolved) || 0
        }))
                
        return mappedResult
    } catch (error) {
        console.error('Error fetching incoming vs resolved rate:', error)
        throw error
    }
}

