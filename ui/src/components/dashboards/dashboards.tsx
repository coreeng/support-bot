'use client'

import { useEffect, useMemo } from 'react'
import { useUrlParams, enumValidator } from '@/lib/hooks/useUrlParams'
import { getDateRangeFromFilter, PRESET_DAYS } from '@/lib/dateRange'
import {
    useFirstResponsePercentiles,
    useTicketResolutionPercentiles,
    useFirstResponseDurationDistribution,
    useUnattendedQueriesCount,
    useTicketResolutionDurationDistribution,
    useResolutionTimesByWeek,
    useUnresolvedTicketAges,
    useIncomingVsResolvedRate,
    useAvgEscalationDurationByTag,
    useEscalationPercentageByTag,
    useEscalationTrendsByDate,
    useEscalationsByTeam,
    useEscalationsByImpact,
    useWeeklyTicketCounts,
    useWeeklyComparison,
    useTopEscalatedTagsThisWeek,
    useResolutionTimeByTag,
    type ResolutionDurationBucket
} from '@/lib/hooks'
import { transformResolutionTimeByTag } from '@/lib/utils'
import { Zap, CheckCircle, AlertTriangle, TrendingUp } from 'lucide-react'

// Section components
import { ResponseSLASection } from './ResponseSLASection'
import { ResolutionSLASection } from './ResolutionSLASection'
import { EscalationSLASection } from './EscalationSLASection'
import { WeeklyTrendsSection } from './WeeklyTrendsSection'

type SectionKey = 'response' | 'resolution' | 'escalation' | 'weekly'

const sections = [
    { key: 'response' as const, label: 'Response SLAs', icon: Zap, color: 'blue' },
    { key: 'resolution' as const, label: 'Resolution SLAs', icon: CheckCircle, color: 'purple' },
    { key: 'escalation' as const, label: 'Escalation SLAs', icon: AlertTriangle, color: 'orange' },
    { key: 'weekly' as const, label: 'Weekly Trends', icon: TrendingUp, color: 'green' },
]

export default function DashboardsPage() {
    // Persist active section, date filter mode, and custom date range in the URL.
    // Validators guard against invalid URL values and auto-correct the URL.
    const [params, setParams] = useUrlParams(
        { section: 'response', dateFilter: 'lastWeek', dateFrom: '', dateTo: '' },
        {
            section: enumValidator(['response', 'resolution', 'escalation', 'weekly'] as const, 'response'),
            dateFilter: enumValidator(['lastWeek', 'last2Weeks', 'lastMonth', 'lastYear', 'custom'] as const, 'lastWeek'),
        },
    )

    // Safe to cast: validators guarantee these are valid enum values.
    const activeSection = params.section as SectionKey
    const dateFilter    = params.dateFilter as 'lastWeek' | 'last2Weeks' | 'lastMonth' | 'lastYear' | 'custom'

    // Correct the URL when custom date range is in an invalid order (dateFrom > dateTo).
    useEffect(() => {
        if (params.dateFilter === 'custom' && params.dateFrom && params.dateTo && params.dateFrom > params.dateTo) {
            setParams({ dateFilter: 'lastWeek', dateFrom: '', dateTo: '' })
        }
    }, [params.dateFilter, params.dateFrom, params.dateTo, setParams])

    // Compute the effective date range for all API calls using the shared utility.
    // Falls back to lastWeek when custom dates are absent — the isDateRangeValid gate
    // prevents hooks from fetching when the user has entered conflicting dates.
    const dateRange = useMemo(
        () =>
            getDateRangeFromFilter({
                dateFilter,
                customDateRange: { start: params.dateFrom || undefined, end: params.dateTo || undefined },
                customValue: 'custom',
                fallbackValue: 'lastWeek',
                presetDays: {
                    lastWeek: PRESET_DAYS.lastWeek,
                    last2Weeks: PRESET_DAYS.last2Weeks,
                    lastMonth: PRESET_DAYS.lastMonth,
                    lastYear: PRESET_DAYS.lastYear,
                },
            }),
        [dateFilter, params.dateFrom, params.dateTo]
    )

    // Only show the invalid-range warning when the user has entered conflicting custom dates.
    const isDateRangeValid = dateFilter !== 'custom' || !params.dateFrom || !params.dateTo || params.dateFrom <= params.dateTo

    // Response SLAs - only load when section is active and date range is valid
    const { data: firstResponsePercentiles, refetch: refetchFirstResponse, isFetching: isFetchingFirstResponse } = useFirstResponsePercentiles(
        activeSection === 'response' && isDateRangeValid,
        dateRange.from,
        dateRange.to
    )
    const { data: durationDistribution, isLoading: isDistributionLoading, refetch: refetchDistribution, isFetching: isFetchingDistribution } = useFirstResponseDurationDistribution(
        activeSection === 'response' && isDateRangeValid,
        dateRange.from,
        dateRange.to
    )
    const { data: unattendedQueries, isLoading: isUnattendedLoading, refetch: refetchUnattended, isFetching: isFetchingUnattended } = useUnattendedQueriesCount(
        activeSection === 'response' && isDateRangeValid,
        dateRange.from,
        dateRange.to
    )
    const isRefreshingResponseSla = isFetchingFirstResponse || isFetchingDistribution || isFetchingUnattended

    // Resolution SLAs - only load when section is active and date range is valid
    const { data: resolutionPercentiles, refetch: refetchResolutionPercentiles, isFetching: isFetchingResolutionPerc } = useTicketResolutionPercentiles(
        activeSection === 'resolution' && isDateRangeValid,
        dateRange.from,
        dateRange.to
    )
    const { data: resolutionDurationDistribution, isLoading: isResolutionDistributionLoading, refetch: refetchResolutionDistribution, isFetching: isFetchingResDist } = useTicketResolutionDurationDistribution(
        activeSection === 'resolution' && isDateRangeValid,
        dateRange.from,
        dateRange.to
    ) as {
        data: ResolutionDurationBucket[] | undefined
        isLoading: boolean
        refetch: () => void
        isFetching: boolean
    }
    const { data: resolutionTimesByWeek, refetch: refetchResolutionByWeek, isFetching: isFetchingResByWeek } = useResolutionTimesByWeek(
        activeSection === 'resolution' && isDateRangeValid,
        dateRange.from,
        dateRange.to
    )
    const { data: unresolvedTicketAges, refetch: refetchUnresolvedAges, isFetching: isFetchingUnresolvedAges } = useUnresolvedTicketAges(
        activeSection === 'resolution' && isDateRangeValid,
        dateRange.from,
        dateRange.to
    )
    const { data: incomingVsResolvedRate, refetch: refetchIncomingVsResolved, isFetching: isFetchingIncomingVsResolved } = useIncomingVsResolvedRate(
        activeSection === 'resolution' && isDateRangeValid,
        dateRange.from,
        dateRange.to
    )

    const { data: avgEscalationDurationByTag, refetch: refetchAvgEscDuration, isFetching: isFetchingAvgEsc } = useAvgEscalationDurationByTag(
        activeSection === 'escalation' && isDateRangeValid,
        dateRange.from,
        dateRange.to
    )
    const { data: escalationPercentageByTag, refetch: refetchEscPercentage, isFetching: isFetchingEscPerc } = useEscalationPercentageByTag(
        activeSection === 'escalation' && isDateRangeValid,
        dateRange.from,
        dateRange.to
    )
    const { data: escalationTrendsByDate, refetch: refetchEscTrends, isFetching: isFetchingEscTrends } = useEscalationTrendsByDate(
        activeSection === 'escalation' && isDateRangeValid,
        dateRange.from,
        dateRange.to
    )
    const { data: escalationsByTeam, refetch: refetchEscByTeam, isFetching: isFetchingEscTeam } = useEscalationsByTeam(
        activeSection === 'escalation' && isDateRangeValid,
        dateRange.from,
        dateRange.to
    )
    const { data: escalationsByImpact, refetch: refetchEscByImpact, isFetching: isFetchingEscImpact } = useEscalationsByImpact(
        activeSection === 'escalation' && isDateRangeValid,
        dateRange.from,
        dateRange.to
    )
    const isRefreshingEscalationSla = isFetchingAvgEsc || isFetchingEscPerc || isFetchingEscTrends || isFetchingEscTeam || isFetchingEscImpact

    // Weekly SLA Trends - only load when section is active
    const { data: weeklyTicketCounts, refetch: refetchWeeklyCounts, isFetching: isFetchingWeeklyCounts } = useWeeklyTicketCounts(
        activeSection === 'weekly'
    )
    const { data: weeklyComparison, refetch: refetchWeeklyComparison, isFetching: isFetchingWeeklyComp } = useWeeklyComparison(
        activeSection === 'weekly'
    )
    const { data: topEscalatedTags, refetch: refetchTopEscTags, isFetching: isFetchingTopEsc } = useTopEscalatedTagsThisWeek(
        activeSection === 'weekly'
    )
    const { data: resolutionTimeByTag, refetch: refetchResTimeByTag, isFetching: isFetchingResTimeByTag } = useResolutionTimeByTag(
        activeSection === 'resolution' && isDateRangeValid,
        dateRange.from,
        dateRange.to
    )

    const isRefreshingResolutionSla = isFetchingResolutionPerc || isFetchingResDist || isFetchingResByWeek || isFetchingUnresolvedAges || isFetchingResTimeByTag || isFetchingIncomingVsResolved
    const isRefreshingWeeklySla = isFetchingWeeklyCounts || isFetchingWeeklyComp || isFetchingTopEsc

    // Convert resolution time by tag from seconds to hours
    const resolutionTimeByTagInHours = useMemo(() => {
        return transformResolutionTimeByTag(resolutionTimeByTag || [])
    }, [resolutionTimeByTag])

    return (
        <div className="min-h-screen bg-gradient-to-br from-gray-50 to-gray-100">
            {/* Sticky Header */}
            <div className="sticky top-0 z-10 bg-white shadow-md border-b border-gray-200">
                <div className="max-w-[1600px] mx-auto px-8 py-4">
                    <div className="flex items-center justify-between mb-3">
                        <div>
                            <h1 className="text-2xl font-bold text-gray-900">SLA Dashboards</h1>
                            <p className="text-xs text-gray-500 mt-0.5">Performance at a glance</p>
                        </div>
                    </div>

                    <div className="flex flex-wrap items-center gap-2 py-2">
                        <select
                            data-testid="sla-date-filter"
                            value={dateFilter}
                            onChange={e => {
                                const next = e.target.value
                                if (next !== 'custom') {
                                    setParams({ dateFilter: next, dateFrom: '', dateTo: '' })
                                } else {
                                    // Pre-fill custom inputs with the current effective range so
                                    // the user sees what they're starting from and we never pass
                                    // undefined to the data hooks.
                                    setParams({
                                        dateFilter: 'custom',
                                        dateFrom: params.dateFrom || dateRange.from || '',
                                        dateTo: params.dateTo || dateRange.to || '',
                                    })
                                }
                            }}
                            className="p-2 border rounded text-xs"
                        >
                            <option value="lastWeek">Last Week</option>
                            <option value="last2Weeks">Last 2 Weeks</option>
                            <option value="lastMonth">Last Month</option>
                            <option value="lastYear">Last Year</option>
                            <option value="custom">Custom</option>
                        </select>

                        {dateFilter === 'custom' && (
                            <>
                                <input
                                    type="date"
                                    value={params.dateFrom}
                                    onChange={e => setParams({ dateFrom: e.target.value })}
                                    className="px-2 py-1 text-xs border border-gray-300 rounded focus:outline-none focus:ring-1 focus:ring-blue-500"
                                />
                                <span className="text-gray-400 text-xs">to</span>
                                <input
                                    type="date"
                                    value={params.dateTo}
                                    onChange={e => setParams({ dateTo: e.target.value })}
                                    className="px-2 py-1 text-xs border border-gray-300 rounded focus:outline-none focus:ring-1 focus:ring-blue-500"
                                />
                            </>
                        )}

                        <span className="text-xs text-gray-500 ml-2">
                            📅 {dateRange.from} → {dateRange.to}
                        </span>

                        {dateFilter === 'custom' && !isDateRangeValid && (
                            <span className="text-xs text-red-600 font-medium ml-2">
                                ⚠️ Invalid range
                            </span>
                        )}
                    </div>
                </div>
            </div>

            <div className="max-w-[1600px] mx-auto px-8 py-6">

                {/* Tab Navigation */}
                <div className="bg-white rounded-xl shadow-lg border border-gray-200 overflow-hidden">
                    {/* Tab Headers */}
                    <div className="flex border-b border-gray-200 bg-gradient-to-r from-gray-50 to-gray-100">
                        {sections.map((section) => {
                            const Icon = section.icon
                            const isActive = activeSection === section.key
                            const colorClasses: Record<string, string> = {
                                blue: isActive ? 'border-blue-600 bg-blue-50' : 'border-transparent hover:bg-blue-50',
                                purple: isActive ? 'border-purple-600 bg-purple-50' : 'border-transparent hover:bg-purple-50',
                                orange: isActive ? 'border-orange-600 bg-orange-50' : 'border-transparent hover:bg-orange-50',
                                green: isActive ? 'border-green-600 bg-green-50' : 'border-transparent hover:bg-green-50',
                            }

                            const textColor = isActive
                                ? section.color === 'blue' ? 'text-blue-700'
                                : section.color === 'purple' ? 'text-purple-700'
                                : section.color === 'orange' ? 'text-orange-700'
                                : 'text-green-700'
                                : 'text-gray-600'

                            return (
                                <button
                                    key={section.key}
                                    onClick={() => setParams({ section: section.key })}
                                    className={`flex-1 flex items-center justify-center gap-2 px-6 py-4 text-sm font-semibold border-b-3 transition-all duration-200 ${colorClasses[section.color]}`}
                                >
                                    <Icon className={`w-5 h-5 ${isActive ? 'animate-pulse' : ''}`} />
                                    <span className={textColor}>
                                        {section.label}
                                    </span>
                                </button>
                            )
                        })}
                    </div>

                    {/* Tab Content */}
                    <div className="p-8 min-h-[600px] animate-fadeIn">
                        {activeSection === 'response' && (
                            <ResponseSLASection
                                firstResponsePercentiles={firstResponsePercentiles}
                                durationDistribution={durationDistribution}
                                unattendedQueries={unattendedQueries}
                                isDistributionLoading={isDistributionLoading}
                                isUnattendedLoading={isUnattendedLoading}
                                isRefreshing={isRefreshingResponseSla}
                                onRefresh={() => {
                                    refetchFirstResponse()
                                    refetchDistribution()
                                    refetchUnattended()
                                }}
                            />
                        )}

                        {activeSection === 'resolution' && (
                            <ResolutionSLASection
                                resolutionPercentiles={resolutionPercentiles}
                                resolutionDurationDistribution={resolutionDurationDistribution}
                                resolutionTimesByWeek={resolutionTimesByWeek}
                                resolutionTimeByTagInHours={resolutionTimeByTagInHours}
                                unresolvedTicketAges={unresolvedTicketAges}
                                incomingVsResolvedRate={incomingVsResolvedRate}
                                isResolutionDistributionLoading={isResolutionDistributionLoading}
                                isRefreshing={isRefreshingResolutionSla}
                                onRefresh={() => {
                                    refetchResolutionPercentiles()
                                    refetchResolutionDistribution()
                                    refetchResolutionByWeek()
                                    refetchUnresolvedAges()
                                    refetchResTimeByTag()
                                    refetchIncomingVsResolved()
                                }}
                            />
                        )}

                        {activeSection === 'escalation' && (
                            <EscalationSLASection
                                avgEscalationDurationByTag={avgEscalationDurationByTag}
                                escalationPercentageByTag={escalationPercentageByTag}
                                escalationTrendsByDate={escalationTrendsByDate}
                                escalationsByTeam={escalationsByTeam}
                                escalationsByImpact={escalationsByImpact}
                                isRefreshing={isRefreshingEscalationSla}
                                onRefresh={() => {
                                    refetchAvgEscDuration()
                                    refetchEscPercentage()
                                    refetchEscTrends()
                                    refetchEscByTeam()
                                    refetchEscByImpact()
                                }}
                            />
                        )}

                        {activeSection === 'weekly' && (
                            <WeeklyTrendsSection
                                weeklyComparison={weeklyComparison}
                                weeklyTicketCounts={weeklyTicketCounts}
                                topEscalatedTags={topEscalatedTags}
                                isRefreshing={isRefreshingWeeklySla}
                                onRefresh={() => {
                                    refetchWeeklyCounts()
                                    refetchWeeklyComparison()
                                    refetchTopEscTags()
                                }}
                            />
                        )}
                    </div>
                </div>
            </div>
        </div>
    )
}
