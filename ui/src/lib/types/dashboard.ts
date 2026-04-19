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

export type IncomingVsResolvedGranularity = 'HOUR' | 'DAY' | 'WEEK'

export type IncomingVsResolvedRequestGranularity = 'AUTO' | IncomingVsResolvedGranularity

export interface IncomingVsResolvedRatePoint {
    [key: string]: string | number
    time: string
    incoming: number
    resolved: number
}

export interface IncomingVsResolvedRate {
    granularity: IncomingVsResolvedGranularity
    data: IncomingVsResolvedRatePoint[]
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
 * Per-repository PR insights from /tenant-insights/pr-stats
 */
export interface RepoInsights {
    repo: string
    owningTeam: string
    prCount: number
    openCount: number
    escalatedCount: number
    breachedCount: number
    p50Seconds: number
    p90Seconds: number
    p99Seconds: number
    /** True if any PR in this repo had an sla_deadline set within the queried window.
     *  When false, breachedCount is always 0 and the Breached column is suppressed in the UI.
     *  Note: this is a window-scoped aggregate — a repo with SLA config may show false if the
     *  date filter excludes all SLA-tracked PRs.
     *  Optional at the type level to stay safe under API/UI version skew; callers should treat
     *  `undefined` as "unknown" rather than collapsing to "no SLA". */
    hasSla?: boolean
}

/**
 * Escalation breakdown from /tenant-insights/escalation-breakdown
 */
export interface EscalationBreakdown {
    totalPrTickets: number
    botEscalatedTickets: number
    manuallyEscalatedTickets: number
}

/**
 * A single in-flight (currently open) pull request returned by
 * /tenant-insights/in-flight-prs
 */
export interface InFlightPr {
    githubRepo: string
    prNumber: number
    prUrl: string
    status: string
    waitingOn: string
    prCreatedAt: string
    slaDeadline: string | null
    slaRemainingSeconds: number | null
    lastReviewAt: string | null
    owningTeam: string
    owningTeamLabel: string
    ticketChannelId: string
    ticketQueryTs: string
    escalatedAt: string | null
    /** True when the PR has an SLA configured. When true, exactly one of slaDeadline or
     *  slaRemainingSeconds is expected to be non-null (enforced server-side). When false, both
     *  slaDeadline and slaRemainingSeconds are null.
     *  Distinct from a paused SLA (hasSla=true, slaDeadline=null, slaRemainingSeconds set).
     *  Optional at the type level to stay safe under API/UI version skew; callers should treat
     *  `undefined` as "unknown" rather than collapsing to "no SLA". */
    hasSla?: boolean
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

