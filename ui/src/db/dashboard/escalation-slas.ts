// src/db/dashboard/escalation-slas.ts
import prisma from '@/lib/prisma'
import { Prisma } from '@prisma/client'

/**
 * Get average escalation duration by tag (in hours)
 * Limited to top 15 tags by average duration to keep charts readable
 * Uses views: aggregated_ticket_data + aggregated_escalation_data
 */
export async function getAvgEscalationDurationByTag(dateFrom?: string, dateTo?: string) {
    try {
        const dateFilter = dateFrom && dateTo
            ? Prisma.sql`AND ed.open_ts::date >= ${dateFrom}::date 
                         AND ed.open_ts::date <= ${dateTo}::date`
            : Prisma.empty
        
        const result = await prisma.$queryRaw<{
            tag: string
            avg_duration: number | null
        }[]>`
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
              ${dateFilter}
            GROUP BY tag
            HAVING AVG(EXTRACT(EPOCH FROM business_time_between(ed.open_ts, ed.resolved_ts, 'Europe/London'))) > 0
            ORDER BY avg_duration DESC NULLS LAST
            LIMIT 15;
        `;
        
        return result
            .map(row => ({
                tag: row.tag,
                avgDuration: Number(row.avg_duration) || 0
            }))
            .filter(item => item.avgDuration > 0); // Extra safety filter
    } catch (error) {
        console.error('Error fetching avg escalation duration by tag:', error)
        throw error
    }
}

/**
 * Get escalation count by tags
 */
export async function getEscalationPercentageByTag(dateFrom?: string, dateTo?: string) {
    try {
        const dateFilter = dateFrom && dateTo
            ? Prisma.sql`AND ed.open_ts::date >= ${dateFrom}::date 
                         AND ed.open_ts::date <= ${dateTo}::date`
            : Prisma.empty
        
        const result = await prisma.$queryRaw<{
            tag: string
            count: bigint
        }[]>`
            SELECT 
                unnest(ed.tags) AS tag,
                COUNT(*) AS count
            FROM aggregated_escalation_data ed
            WHERE ed.open_ts IS NOT NULL
              AND ed.tags IS NOT NULL
              ${dateFilter}
            GROUP BY tag
            HAVING COUNT(*) > 0
            ORDER BY count DESC
            LIMIT 15;
        `;
        
        return result
            .map(row => ({
                tag: row.tag,
                count: Number(row.count) || 0
            }))
            .filter(item => item.count > 0); // Extra safety filter
    } catch (error) {
        console.error('Error fetching escalation percentage by tag:', error)
        throw error
    }
}

/**
 * Get escalation trends by date over time
 */
export async function getEscalationTrendsByDate(dateFrom?: string, dateTo?: string) {
    try {
        const dateFilter = dateFrom && dateTo
            ? Prisma.sql`AND ed.open_ts::date >= ${dateFrom}::date 
                         AND ed.open_ts::date <= ${dateTo}::date`
            : Prisma.empty
        
        const result = await prisma.$queryRaw<{
            escalation_date: Date
            escalations: bigint
        }[]>`
            SELECT
                DATE_TRUNC('day', ed.open_ts::timestamptz) AS escalation_date,
                COUNT(ed.escalation_id) AS escalations
            FROM aggregated_escalation_data ed
            WHERE ed.open_ts IS NOT NULL
              ${dateFilter}
            GROUP BY escalation_date
            ORDER BY escalation_date;
        `;
        
        return result.map(row => ({
            date: row.escalation_date.toISOString().split('T')[0],
            escalations: Number(row.escalations) || 0
        }));
    } catch (error) {
        console.error('Error fetching escalation trends by date:', error)
        throw error
    }
}

/**
 * Get escalations by team (assignee)
 * Limited to top 10 teams to keep charts readable
 */
export async function getEscalationsByTeam(dateFrom?: string, dateTo?: string) {
    try {
        const dateFilter = dateFrom && dateTo
            ? Prisma.sql`AND td.first_open_ts::date >= ${dateFrom}::date 
                         AND td.first_open_ts::date <= ${dateTo}::date`
            : Prisma.empty
        
        const result = await prisma.$queryRaw<{
            team_name: string | null
            total_escalations: bigint
        }[]>`
            SELECT 
                COALESCE(ed.team_id, 'Unassigned') AS team_name,
                COUNT(ed.escalation_id) AS total_escalations
            FROM aggregated_escalation_data ed
            INNER JOIN aggregated_ticket_data td ON ed.ticket_id = td.ticket_id
            WHERE ed.open_ts IS NOT NULL
              ${dateFilter}
            GROUP BY ed.team_id
            HAVING COUNT(ed.escalation_id) > 0
            ORDER BY total_escalations DESC
            LIMIT 10;
        `;
        
        return result
            .map(row => ({
                assigneeName: row.team_name || 'Unassigned',
                totalEscalations: Number(row.total_escalations) || 0
            }))
            .filter(item => item.totalEscalations > 0);
    } catch (error) {
        console.error('Error fetching escalations by team:', error)
        throw error
    }
}

/**
 * Get escalations by impact level
 */
export async function getEscalationsByImpact(dateFrom?: string, dateTo?: string) {
    try {
        const dateFilter = dateFrom && dateTo
            ? Prisma.sql`AND ed.open_ts::date >= ${dateFrom}::date 
                         AND ed.open_ts::date <= ${dateTo}::date`
            : Prisma.empty
        
        const result = await prisma.$queryRaw<{
            impact_level: string | null
            total_escalations: bigint
        }[]>`
            SELECT 
                COALESCE(td.impact, 'Not yet tagged') AS impact_level,
                COUNT(ed.escalation_id) AS total_escalations
            FROM aggregated_escalation_data ed
            INNER JOIN aggregated_ticket_data td ON ed.ticket_id = td.ticket_id
            WHERE ed.open_ts IS NOT NULL
              ${dateFilter}
            GROUP BY td.impact
            HAVING COUNT(ed.escalation_id) > 0
            ORDER BY total_escalations DESC;
        `;
        
        return result
            .map(row => ({
                impactLevel: row.impact_level || 'Not yet tagged',
                totalEscalations: Number(row.total_escalations) || 0
            }))
            .filter(item => item.totalEscalations > 0);
    } catch (error) {
        console.error('Error fetching escalations by impact:', error)
        throw error
    }
}

