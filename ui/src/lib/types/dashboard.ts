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
    /** Whether this repo is currently SLA-tracked according to present-day backend config.
     *  Decoupled from the historical metrics in this same row: the backend overrides the stored
     *  per-PR `has_sla` aggregate with the current SLA config on the `/pr-stats` path, so:
     *    - a repo reconfigured SLA → no-SLA (or removed from config) ships hasSla=false even when
     *      breachedCount > 0 from historical SLA'd rows;
     *    - a repo newly added to SLA config ships hasSla=true even when every stored row was
     *      tracked without an SLA.
     *  UI rule: when false, hide/suppress the Breached column for this row, but do NOT assume
     *  breachedCount is zero on the wire.
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
    /** Whether this PR was created under an SLA (per-row truth, authoritative at row granularity —
     *  unlike RepoInsights.hasSla, which reflects present-day repo config). The backend does NOT
     *  enforce a consistency invariant between hasSla and the (slaDeadline, slaRemainingSeconds)
     *  pair: UI consumers must tolerate every combination and classify them explicitly.
     *  Expected combinations (healthy):
     *    - hasSla=false, slaDeadline=null, slaRemainingSeconds=null → No SLA.
     *    - hasSla=true,  slaDeadline set,  slaRemainingSeconds=null → Active SLA.
     *    - hasSla=true,  slaDeadline=null, slaRemainingSeconds set  → Paused (tenant turn).
     *  Tolerated-but-anomalous (may appear under data drift):
     *    - hasSla=true,  both null → "SLA data missing" — surface as a distinct badge, not as
     *      "No SLA" (which would hide the anomaly).
     *    - hasSla=false with either SLA field set → treat as No SLA and warn.
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

