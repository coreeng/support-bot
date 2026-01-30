// src/db/dashboard/response-slas.ts
import prisma from '@/lib/prisma'
import { Prisma } from '@prisma/client'

/**
 * Get first response duration distribution (for histogram/chart)
 * Returns duration in seconds for each query/ticket
 * Uses aggregated_ticket_data view only
 */
export async function getFirstResponseDurationDistribution(dateFrom?: string, dateTo?: string) {
    try {
        const dateFilter = dateFrom && dateTo 
            ? Prisma.sql`AND query_posted_ts::date >= ${dateFrom}::date AND query_posted_ts::date <= ${dateTo}::date` 
            : Prisma.empty
        
        const durations = await prisma.$queryRaw<{ duration: number }[]>`
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
              ${dateFilter}
            ORDER BY ticket_id;
        `;
        
        return durations.map(d => Number(d.duration) || 0).filter(d => d > 0);
    } catch (error) {
        console.error('Error fetching first response duration distribution:', error)
        throw error
    }
}

/**
 * Get first response percentiles (P50, P90)
 * Uses business_time_between function to calculate working hours
 */
export async function getFirstResponsePercentiles(dateFrom?: string, dateTo?: string) {
    try {
        const dateFilter = dateFrom && dateTo
            ? Prisma.sql`AND first_open_ts::date >= ${dateFrom}::date 
                         AND first_open_ts::date <= ${dateTo}::date`
            : Prisma.empty
        
        const result = await prisma.$queryRaw<{
            p50: number
            p90: number
        }[]>`
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
                  ${dateFilter}
            )
            SELECT 
                percentile_cont(0.5) WITHIN GROUP (ORDER BY duration) AS p50,
                percentile_cont(0.9) WITHIN GROUP (ORDER BY duration) AS p90
            FROM response_durations
            WHERE duration IS NOT NULL AND duration > 0;
        `;

        return result[0] || { p50: 0, p90: 0 };
    } catch (error) {
        console.error('Error fetching first response percentiles:', error)
        throw error
    }
}

/**
 * Get unattended queries count
 * Queries that don't have a corresponding ticket (ticket_id IS NULL in view)
 * Uses aggregated_ticket_data view only
 */
export async function getUnattendedQueriesCount(dateFrom?: string, dateTo?: string) {
    try {
        const dateFilter = dateFrom && dateTo
            ? Prisma.sql`AND query_posted_ts::date >= ${dateFrom}::date AND query_posted_ts::date <= ${dateTo}::date`
            : Prisma.empty
        
        const result = await prisma.$queryRaw<{ count: bigint }[]>`
            SELECT COUNT(*) AS count
            FROM aggregated_ticket_data
            WHERE ticket_id IS NULL
              ${dateFilter};
        `
        
        const count = Number(result[0]?.count || 0)
        return { count }
    } catch (error) {
        console.error('Error fetching unattended queries count:', error)
        throw error
    }
}

