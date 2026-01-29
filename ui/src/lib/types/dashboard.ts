// src/lib/types/dashboard.ts

/**
 * Type definitions for dashboard API responses
 */

export interface DashboardPercentiles {
    p50: number
    p90: number
}

export interface DashboardPercentilesWithP75 extends DashboardPercentiles {
    p75: number
}

export interface UnattendedQueriesCount {
    count: number
}

export interface ResolutionTimesByWeek {
    week: string
    p50: number
    p75: number
    p90: number
}

export interface UnresolvedTicketAges {
    p50: string
    p90: string
}

export interface EscalationDurationByTag {
    tag: string
    avgDuration: number
}

export interface EscalationPercentageByTag {
    tag: string
    count: number
}

export interface EscalationTrendsByDate {
    date: string
    escalations: number
}

export interface EscalationsByTeam {
    assigneeName: string
    totalEscalations: number
}

export interface EscalationsByImpact {
    impactLevel: string
    totalEscalations: number
}

export interface WeeklyTicketCounts {
    week: string
    opened: number
    closed: number
    escalated: number
    stale: number
}

export interface WeeklyComparisonMetric {
    label: string
    thisWeek: number
    lastWeek: number
    change: number
}

export interface TopEscalatedTag {
    tag: string
    count: number
}

export interface ResolutionTimeByTag {
    tag: string
    p50: number
    p90: number
}

/**
 * Histogram data point for distribution charts
 */
export interface HistogramBin {
    range: string
    count: number
}

/**
 * Date range filter parameters
 */
export interface DateRangeFilter {
    dateFrom?: string
    dateTo?: string
}

/**
 * Date filter mode options
 */
export type DateFilterMode = 'week' | 'month' | 'year' | 'custom'

