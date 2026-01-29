// src/db/dashboard/index.ts
// Barrel export for all dashboard functions

// Response SLAs
export {
    getFirstResponseDurationDistribution,
    getFirstResponsePercentiles,
    getUnattendedQueriesCount
} from './response-slas'

// Resolution SLAs
export {
    getTicketResolutionPercentiles,
    getTicketResolutionDurationDistribution,
    getResolutionTimesByWeek,
    getUnresolvedTicketAges,
    getIncomingVsResolvedRate
} from './resolution-slas'

// Escalation SLAs
export {
    getAvgEscalationDurationByTag,
    getEscalationPercentageByTag,
    getEscalationTrendsByDate,
    getEscalationsByTeam,
    getEscalationsByImpact
} from './escalation-slas'

// Weekly Trends
export {
    getWeeklyTicketCounts,
    getWeeklyComparison,
    getTopEscalatedTagsThisWeek,
    getResolutionTimeByTag
} from './weekly-trends'

