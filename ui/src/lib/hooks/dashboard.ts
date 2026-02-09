// src/lib/hooks/dashboard.ts
import { useQuery } from '@tanstack/react-query'
import { apiGet } from '@/lib/api'

/**
 * Dashboard hooks for SLA metrics and analytics
 * All hooks fetch from /dashboard/* endpoints on the backend API
 */

// Helper to build query string
function buildParams(dateFrom?: string, dateTo?: string): string {
    const params = new URLSearchParams()
    if (dateFrom) params.append('dateFrom', dateFrom)
    if (dateTo) params.append('dateTo', dateTo)
    const queryString = params.toString()
    return queryString ? `?${queryString}` : ''
}

// ===== Response SLA Hooks =====

export function useFirstResponseDurationDistribution(enabled = true, dateFrom?: string, dateTo?: string) {
    return useQuery<number[]>({
        queryKey: ['firstResponseDurationDistribution', dateFrom, dateTo],
        queryFn: () => apiGet(`/dashboard/first-response-distribution${buildParams(dateFrom, dateTo)}`),
        enabled,
    })
}

export function useFirstResponsePercentiles(enabled = true, dateFrom?: string, dateTo?: string) {
    return useQuery<{ p50: number; p90: number }>({
        queryKey: ['firstResponsePercentiles', dateFrom, dateTo],
        queryFn: () => apiGet(`/dashboard/first-response-percentiles${buildParams(dateFrom, dateTo)}`),
        enabled,
    })
}

export function useUnattendedQueriesCount(enabled = true, dateFrom?: string, dateTo?: string) {
    return useQuery<{ count: number }>({
        queryKey: ['unattendedQueriesCount', dateFrom, dateTo],
        queryFn: () => apiGet(`/dashboard/unattended-queries-count${buildParams(dateFrom, dateTo)}`),
        enabled,
    })
}

export type ResolutionDurationBucket = { label: string; count: number; minMinutes: number; maxMinutes: number }

// ===== Resolution SLA Hooks =====

export function useTicketResolutionPercentiles(enabled = true, dateFrom?: string, dateTo?: string) {
    return useQuery<{ p50: number; p75: number; p90: number }>({
        queryKey: ['ticketResolutionPercentiles', dateFrom, dateTo],
        queryFn: () => apiGet(`/dashboard/resolution-percentiles${buildParams(dateFrom, dateTo)}`),
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
        queryFn: () => apiGet(`/dashboard/resolution-duration-distribution${buildParams(dateFrom, dateTo)}`),
        enabled,
    })
}

export function useResolutionTimesByWeek(enabled = true, dateFrom?: string, dateTo?: string) {
    return useQuery<{ week: string; p50: number; p75: number; p90: number }[]>({
        queryKey: ['resolutionTimesByWeek', dateFrom, dateTo],
        queryFn: () => apiGet(`/dashboard/resolution-times-by-week${buildParams(dateFrom, dateTo)}`),
        enabled,
    })
}

export function useUnresolvedTicketAges(enabled = true, dateFrom?: string, dateTo?: string) {
    return useQuery<{ p50: string; p90: string }>({
        queryKey: ['unresolvedTicketAges', dateFrom, dateTo],
        queryFn: () => apiGet(`/dashboard/unresolved-ticket-ages${buildParams(dateFrom, dateTo)}`),
        enabled,
    })
}

export function useIncomingVsResolvedRate(enabled = true, dateFrom?: string, dateTo?: string) {
    return useQuery<{ time: string; incoming: number; resolved: number }[]>({
        queryKey: ['incomingVsResolvedRate', dateFrom, dateTo],
        queryFn: () => apiGet(`/dashboard/incoming-vs-resolved-rate${buildParams(dateFrom, dateTo)}`),
        enabled,
    })
}

// ===== Escalation SLA Hooks =====

export function useAvgEscalationDurationByTag(enabled = true, dateFrom?: string, dateTo?: string) {
    return useQuery<{ tag: string; avgDuration: number }[]>({
        queryKey: ['avgEscalationDurationByTag', dateFrom, dateTo],
        queryFn: () => apiGet(`/dashboard/avg-escalation-duration-by-tag${buildParams(dateFrom, dateTo)}`),
        enabled,
    })
}

export function useEscalationPercentageByTag(enabled = true, dateFrom?: string, dateTo?: string) {
    return useQuery<{ tag: string; count: number }[]>({
        queryKey: ['escalationPercentageByTag', dateFrom, dateTo],
        queryFn: () => apiGet(`/dashboard/escalation-percentage-by-tag${buildParams(dateFrom, dateTo)}`),
        enabled,
    })
}

export function useEscalationTrendsByDate(enabled = true, dateFrom?: string, dateTo?: string) {
    return useQuery<{ date: string; escalations: number }[]>({
        queryKey: ['escalationTrendsByDate', dateFrom, dateTo],
        queryFn: () => apiGet(`/dashboard/escalation-trends-by-date${buildParams(dateFrom, dateTo)}`),
        enabled,
    })
}

export function useEscalationsByTeam(enabled = true, dateFrom?: string, dateTo?: string) {
    return useQuery<{ assigneeName: string; totalEscalations: number }[]>({
        queryKey: ['escalationsByTeam', dateFrom, dateTo],
        queryFn: () => apiGet(`/dashboard/escalations-by-team${buildParams(dateFrom, dateTo)}`),
        enabled,
    })
}

export function useEscalationsByImpact(enabled = true, dateFrom?: string, dateTo?: string) {
    return useQuery<{ impactLevel: string; totalEscalations: number }[]>({
        queryKey: ['escalationsByImpact', dateFrom, dateTo],
        queryFn: () => apiGet(`/dashboard/escalations-by-impact${buildParams(dateFrom, dateTo)}`),
        enabled,
    })
}

// ===== Weekly Trends Hooks =====

export function useWeeklyTicketCounts(enabled = true) {
    return useQuery<{ week: string; opened: number; closed: number; escalated: number; stale: number }[]>({
        queryKey: ['weeklyTicketCounts'],
        queryFn: () => apiGet('/dashboard/weekly-ticket-counts'),
        enabled,
    })
}

export function useWeeklyComparison(enabled = true) {
    return useQuery<{ label: string; thisWeek: number; lastWeek: number; change: number }[]>({
        queryKey: ['weeklyComparison'],
        queryFn: () => apiGet('/dashboard/weekly-comparison'),
        enabled,
    })
}

export function useTopEscalatedTagsThisWeek(enabled = true) {
    return useQuery<{ tag: string; count: number }[]>({
        queryKey: ['topEscalatedTagsThisWeek'],
        queryFn: () => apiGet('/dashboard/top-escalated-tags-this-week'),
        enabled,
    })
}

export function useResolutionTimeByTag(enabled = true, startDate?: string, endDate?: string) {
    return useQuery<{ tag: string; p50: number; p90: number }[]>({
        queryKey: ['resolutionTimeByTag', startDate, endDate],
        queryFn: () => apiGet(`/dashboard/resolution-time-by-tag${buildParams(startDate, endDate)}`),
        enabled,
    })
}
