// src/lib/hooks/dashboard.ts
import { useQuery } from '@tanstack/react-query'
import { buildDateQuery } from '@/lib/utils'

/**
 * Dashboard hooks for SLA metrics and analytics
 * All hooks fetch from /api/db/dashboard/* endpoints
 */

// ===== Response SLA Hooks =====

export function useFirstResponseDurationDistribution(enabled = true, dateFrom?: string, dateTo?: string) {
    return useQuery<number[]>({
        queryKey: ['firstResponseDurationDistribution', dateFrom, dateTo],
        queryFn: async () => {
            const params = new URLSearchParams()
            if (dateFrom) params.append('dateFrom', dateFrom)
            if (dateTo) params.append('dateTo', dateTo)
            const queryString = params.toString()
            const res = await fetch(`/api/db/dashboard/first-response-distribution${queryString ? `?${queryString}` : ''}`)
            if (!res.ok) throw new Error('Failed to fetch first response duration distribution')
            return res.json()
        },
        enabled,
    })
}

export function useFirstResponsePercentiles(enabled = true, dateFrom?: string, dateTo?: string) {
    return useQuery<{ p50: number; p90: number }>({
        queryKey: ['firstResponsePercentiles', dateFrom, dateTo],
        queryFn: async () => {
            const params = new URLSearchParams()
            if (dateFrom) params.append('dateFrom', dateFrom)
            if (dateTo) params.append('dateTo', dateTo)
            const queryString = params.toString()
            const res = await fetch(`/api/db/dashboard/first-response-percentiles${queryString ? `?${queryString}` : ''}`)
            if (!res.ok) throw new Error('Failed to fetch first response percentiles')
            return res.json()
        },
        enabled,
    })
}

export function useUnattendedQueriesCount(enabled = true, dateFrom?: string, dateTo?: string) {
    return useQuery<{ count: number }>({
        queryKey: ['unattendedQueriesCount', dateFrom, dateTo],
        queryFn: async () => {
            const params = new URLSearchParams()
            if (dateFrom) params.append('dateFrom', dateFrom)
            if (dateTo) params.append('dateTo', dateTo)
            const queryString = params.toString()
            const res = await fetch(`/api/db/dashboard/unattended-queries-count${queryString ? `?${queryString}` : ''}`)
            if (!res.ok) throw new Error('Failed to fetch unattended queries count')
            return res.json()
        },
        enabled,
    })
}

export type ResolutionDurationBucket = { label: string; count: number; minMinutes: number; maxMinutes: number }

// ===== Resolution SLA Hooks =====

export function useTicketResolutionPercentiles(enabled = true, dateFrom?: string, dateTo?: string) {
    return useQuery<{ p50: number; p75: number; p90: number }>({
        queryKey: ['ticketResolutionPercentiles', dateFrom, dateTo],
        queryFn: async () => {
            const params = new URLSearchParams()
            if (dateFrom) params.append('dateFrom', dateFrom)
            if (dateTo) params.append('dateTo', dateTo)
            const queryString = params.toString()
            const res = await fetch(`/api/db/dashboard/resolution-percentiles${queryString ? `?${queryString}` : ''}`)
            if (!res.ok) throw new Error('Failed to fetch ticket resolution percentiles')
            return res.json()
        },
        enabled,
    })
}

export function useTicketResolutionDurationDistribution(
    enabled = true,
    dateFrom?: string,
    dateTo?: string
) {
    return useQuery<ResolutionDurationBucket[], Error>({
        queryKey: ['ticketResolutionDurationDistribution', dateFrom, dateTo],
        queryFn: async () => {
            const params = new URLSearchParams()
            if (dateFrom) params.append('dateFrom', dateFrom)
            if (dateTo) params.append('dateTo', dateTo)
            const queryString = params.toString()
            const res = await fetch(`/api/db/dashboard/resolution-duration-distribution${queryString ? `?${queryString}` : ''}`)
            if (!res.ok) throw new Error('Failed to fetch ticket resolution duration distribution')
            return res.json()
        },
        enabled,
    })
}

export function useResolutionTimesByWeek(enabled = true, dateFrom?: string, dateTo?: string) {
    return useQuery<{ week: string; p50: number; p75: number; p90: number }[]>({
        queryKey: ['resolutionTimesByWeek', dateFrom, dateTo],
        queryFn: async () => {
            const params = new URLSearchParams()
            if (dateFrom) params.append('dateFrom', dateFrom)
            if (dateTo) params.append('dateTo', dateTo)
            const queryString = params.toString()
            const res = await fetch(`/api/db/dashboard/resolution-times-by-week${queryString ? `?${queryString}` : ''}`)
            if (!res.ok) throw new Error('Failed to fetch resolution times by week')
            return res.json()
        },
        enabled,
    })
}

export function useUnresolvedTicketAges(enabled = true, dateFrom?: string, dateTo?: string) {
    return useQuery<{ p50: string; p90: string }>({
        queryKey: ['unresolvedTicketAges', dateFrom, dateTo],
        queryFn: async () => {
            const params = new URLSearchParams()
            if (dateFrom) params.append('dateFrom', dateFrom)
            if (dateTo) params.append('dateTo', dateTo)
            const queryString = params.toString()
            const res = await fetch(`/api/db/dashboard/unresolved-ticket-ages${queryString ? `?${queryString}` : ''}`)
            if (!res.ok) throw new Error('Failed to fetch unresolved ticket ages')
            return res.json()
        },
        enabled,
    })
}

export function useIncomingVsResolvedRate(enabled = true, dateFrom?: string, dateTo?: string) {
    return useQuery<{ time: string; incoming: number; resolved: number }[]>({
        queryKey: ['incomingVsResolvedRate', dateFrom, dateTo],
        queryFn: async () => {
            const params = new URLSearchParams()
            if (dateFrom) params.append('dateFrom', dateFrom)
            if (dateTo) params.append('dateTo', dateTo)
            const queryString = params.toString()
            const res = await fetch(`/api/db/dashboard/incoming-vs-resolved-rate${queryString ? `?${queryString}` : ''}`)
            if (!res.ok) throw new Error('Failed to fetch incoming vs resolved rate')
            return res.json()
        },
        enabled,
    })
}

// ===== Escalation SLA Hooks =====

export function useAvgEscalationDurationByTag(enabled = true, dateFrom?: string, dateTo?: string) {
    return useQuery<{ tag: string; avgDuration: number }[]>({
        queryKey: ['avgEscalationDurationByTag', dateFrom, dateTo],
        queryFn: async () => {
            const params = new URLSearchParams()
            if (dateFrom) params.append('dateFrom', dateFrom)
            if (dateTo) params.append('dateTo', dateTo)
            const queryString = params.toString()
            const res = await fetch(`/api/db/dashboard/avg-escalation-duration-by-tag${queryString ? `?${queryString}` : ''}`)
            if (!res.ok) throw new Error('Failed to fetch avg escalation duration by tag')
            return res.json()
        },
        enabled,
    })
}

export function useEscalationPercentageByTag(enabled = true, dateFrom?: string, dateTo?: string) {
    return useQuery<{ tag: string; count: number }[]>({
        queryKey: ['escalationPercentageByTag', dateFrom, dateTo],
        queryFn: async () => {
            const params = new URLSearchParams()
            if (dateFrom) params.append('dateFrom', dateFrom)
            if (dateTo) params.append('dateTo', dateTo)
            const queryString = params.toString()
            const res = await fetch(`/api/db/dashboard/escalation-percentage-by-tag${queryString ? `?${queryString}` : ''}`)
            if (!res.ok) throw new Error('Failed to fetch escalation percentage by tag')
            return res.json()
        },
        enabled,
    })
}

export function useEscalationTrendsByDate(enabled = true, dateFrom?: string, dateTo?: string) {
    return useQuery<{ date: string; escalations: number }[]>({
        queryKey: ['escalationTrendsByDate', dateFrom, dateTo],
        queryFn: async () => {
            const params = new URLSearchParams()
            if (dateFrom) params.append('dateFrom', dateFrom)
            if (dateTo) params.append('dateTo', dateTo)
            const queryString = params.toString()
            const res = await fetch(`/api/db/dashboard/escalation-trends-by-date${queryString ? `?${queryString}` : ''}`)
            if (!res.ok) throw new Error('Failed to fetch escalation trends by date')
            return res.json()
        },
        enabled,
    })
}

export function useEscalationsByTeam(enabled = true, dateFrom?: string, dateTo?: string) {
    return useQuery<{ assigneeName: string; totalEscalations: number }[]>({
        queryKey: ['escalationsByTeam', dateFrom, dateTo],
        queryFn: async () => {
            const params = new URLSearchParams()
            if (dateFrom) params.append('dateFrom', dateFrom)
            if (dateTo) params.append('dateTo', dateTo)
            const queryString = params.toString()
            const res = await fetch(`/api/db/dashboard/escalations-by-team${queryString ? `?${queryString}` : ''}`)
            if (!res.ok) throw new Error('Failed to fetch escalations by team')
            return res.json()
        },
        enabled,
    })
}

export function useEscalationsByImpact(enabled = true, dateFrom?: string, dateTo?: string) {
    return useQuery<{ impactLevel: string; totalEscalations: number }[]>({
        queryKey: ['escalationsByImpact', dateFrom, dateTo],
        queryFn: async () => {
            const params = new URLSearchParams()
            if (dateFrom) params.append('dateFrom', dateFrom)
            if (dateTo) params.append('dateTo', dateTo)
            const queryString = params.toString()
            const res = await fetch(`/api/db/dashboard/escalations-by-impact${queryString ? `?${queryString}` : ''}`)
            if (!res.ok) throw new Error('Failed to fetch escalations by impact')
            return res.json()
        },
        enabled,
    })
}

// ===== Weekly Trends Hooks =====

export function useWeeklyTicketCounts(enabled = true) {
    return useQuery<{ week: string; opened: number; closed: number; escalated: number; stale: number }[]>({
        queryKey: ['weeklyTicketCounts'],
        queryFn: async () => {
            const res = await fetch('/api/db/dashboard/weekly-ticket-counts')
            if (!res.ok) throw new Error('Failed to fetch weekly ticket counts')
            return res.json()
        },
        enabled,
    })
}

export function useWeeklyComparison(enabled = true) {
    return useQuery<{ label: string; thisWeek: number; lastWeek: number; change: number }[]>({
        queryKey: ['weeklyComparison'],
        queryFn: async () => {
            const res = await fetch('/api/db/dashboard/weekly-comparison')
            if (!res.ok) throw new Error('Failed to fetch weekly comparison')
            return res.json()
        },
        enabled,
    })
}

export function useTopEscalatedTagsThisWeek(enabled = true) {
    return useQuery<{ tag: string; count: number }[]>({
        queryKey: ['topEscalatedTagsThisWeek'],
        queryFn: async () => {
            const res = await fetch('/api/db/dashboard/top-escalated-tags-this-week')
            if (!res.ok) throw new Error('Failed to fetch top escalated tags this week')
            return res.json()
        },
        enabled,
    })
}

export function useResolutionTimeByTag(enabled = true, startDate?: string, endDate?: string) {
    return useQuery<{ tag: string; p50: number; p90: number }[]>({
        queryKey: ['resolutionTimeByTag', startDate, endDate],
        queryFn: async () => {
            const query = buildDateQuery(startDate, endDate)
            const res = await fetch(`/api/db/dashboard/resolution-time-by-tag${query}`)
            if (!res.ok) throw new Error('Failed to fetch resolution time by tag')
            return res.json()
        },
        enabled,
    })
}

