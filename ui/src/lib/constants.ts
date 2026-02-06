// src/lib/constants.ts

/**
 * React Query keys for consistent cache management
 */
export const QUERY_KEYS = {
    // Dashboard metrics
    FIRST_RESPONSE_DIST: 'firstResponseDurationDistribution',
    FIRST_RESPONSE_PERC: 'firstResponsePercentiles',
    UNATTENDED_QUERIES: 'unattendedQueriesCount',
    
    RESOLUTION_PERC: 'ticketResolutionPercentiles',
    RESOLUTION_DIST: 'ticketResolutionDurationDistribution',
    RESOLUTION_BY_WEEK: 'resolutionTimesByWeek',
    UNRESOLVED_AGES: 'unresolvedTicketAges',
    
    ESCALATION_AVG_DURATION: 'avgEscalationDurationByTag',
    ESCALATION_PERCENTAGE: 'escalationPercentageByTag',
    ESCALATION_TRENDS: 'escalationTrendsByDate',
    ESCALATIONS_BY_TEAM: 'escalationsByTeam',
    ESCALATIONS_BY_IMPACT: 'escalationsByImpact',
    
    WEEKLY_COUNTS: 'weeklyTicketCounts',
    WEEKLY_COMPARISON: 'weeklyComparison',
    TOP_ESCALATED_TAGS: 'topEscalatedTagsThisWeek',
    RESOLUTION_BY_TAG: 'resolutionTimeByTag',
    
    // Backend API
    TICKETS: 'tickets',
    TICKET: 'ticket',
    ESCALATIONS: 'escalations',
    TEAMS: 'team',
    RATINGS: 'ratings',
    REGISTRY: 'registry',
} as const

/**
 * API endpoint paths (relative to API_URL)
 */
export const API_ENDPOINTS = {
    DASHBOARD: '/dashboard',
    TICKETS: '/ticket',
    ESCALATIONS: '/escalation',
    TEAMS: '/team',
    RATINGS: '/rating',
    REGISTRY: '/registry',
} as const

/**
 * Default date range values
 */
export const DATE_RANGES = {
    LAST_WEEK_DAYS: 7,
    LAST_MONTH_DAYS: 30,
    LAST_YEAR_DAYS: 365,
} as const

/**
 * Chart color schemes (avoiding red for negative connotations)
 */
export const CHART_COLORS = {
    PRIMARY: '#3B82F6',      // blue-500
    SECONDARY: '#8B5CF6',    // purple-500
    ACCENT: '#10B981',       // green-500
    TERTIARY: '#06B6D4',     // cyan-500
    NEUTRAL: '#6B7280',      // gray-500
} as const

/**
 * SLA thresholds (in seconds)
 */
export const SLA_THRESHOLDS = {
    FIRST_RESPONSE_TARGET: 300,      // 5 minutes
    RESOLUTION_TARGET: 86400,        // 24 hours
    ESCALATION_RESPONSE: 3600,       // 1 hour
} as const

