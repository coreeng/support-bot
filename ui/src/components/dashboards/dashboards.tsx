'use client'

import React, { useMemo, useState, useEffect } from 'react'
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
    // Active section (tab-based navigation)
    const [activeSection, setActiveSection] = useState<SectionKey>(() => {
        // Only runs on client during initial render
        if (typeof window === 'undefined') return 'response'
        const params = new URLSearchParams(window.location.search)
        const section = params.get('section') as SectionKey
        if (section && sections.some(s => s.key === section)) {
            return section
        }
        return 'response'
    })

    useEffect(() => {
        const params = new URLSearchParams(window.location.search)
        params.set('section', activeSection)
        const newUrl = `${window.location.pathname}?${params.toString()}`
        window.history.replaceState({}, '', newUrl)
    }, [activeSection])
    
    // Global date filter - default to last month
    const [dateFilterMode, setDateFilterMode] = useState<'week' | 'month' | 'year' | 'custom'>('week')
    const [startDate, setStartDate] = useState<string>(() => {
        const date = new Date()
        date.setMonth(date.getMonth() - 1)
        return date.toISOString().split('T')[0]
    })
    const [endDate, setEndDate] = useState<string>(() => {
        return new Date().toISOString().split('T')[0]
    })
    const isDateRangeValid = startDate <= endDate
    
    // Quick filter handlers
    const setLastWeek = () => {
        const end = new Date()
        const start = new Date()
        start.setDate(start.getDate() - 7)
        setStartDate(start.toISOString().split('T')[0])
        setEndDate(end.toISOString().split('T')[0])
        setDateFilterMode('week')
    }

    const setLastMonth = () => {
        const end = new Date()
        const start = new Date()
        start.setMonth(start.getMonth() - 1)
        setStartDate(start.toISOString().split('T')[0])
        setEndDate(end.toISOString().split('T')[0])
        setDateFilterMode('month')
    }

    const setLastYear = () => {
        const end = new Date()
        const start = new Date()
        start.setFullYear(start.getFullYear() - 1)
        setStartDate(start.toISOString().split('T')[0])
        setEndDate(end.toISOString().split('T')[0])
        setDateFilterMode('year')
    }

    // When switching to custom mode, ensure dates are preserved if they're not already set
    // This prevents fetching all tickets when custom mode is selected
    const handleCustomModeClick = () => {
        // If dates are not valid or empty, preserve the current week's range (default)
        // This ensures we don't fetch all tickets when switching to custom mode
        if (!isDateRangeValid || !startDate || !endDate) {
            const end = new Date()
            const start = new Date()
            start.setDate(end.getDate() - 7)
            setStartDate(start.toISOString().split('T')[0])
            setEndDate(end.toISOString().split('T')[0])
        }
        setDateFilterMode('custom')
    }

    // Response SLAs - only load when section is active and date range is valid
    const { data: firstResponsePercentiles, refetch: refetchFirstResponse, isFetching: isFetchingFirstResponse } = useFirstResponsePercentiles(
        activeSection === 'response' && isDateRangeValid,
        startDate,
        endDate
    )
    const { data: durationDistribution, isLoading: isDistributionLoading, refetch: refetchDistribution, isFetching: isFetchingDistribution } = useFirstResponseDurationDistribution(
        activeSection === 'response' && isDateRangeValid,
        startDate,
        endDate
    )
    const { data: unattendedQueries, isLoading: isUnattendedLoading, refetch: refetchUnattended, isFetching: isFetchingUnattended } = useUnattendedQueriesCount(
        activeSection === 'response' && isDateRangeValid,
        startDate,
        endDate
    )
    const isRefreshingResponseSla = isFetchingFirstResponse || isFetchingDistribution || isFetchingUnattended
    
    // Resolution SLAs - only load when section is active and date range is valid
    const { data: resolutionPercentiles, refetch: refetchResolutionPercentiles, isFetching: isFetchingResolutionPerc } = useTicketResolutionPercentiles(
        activeSection === 'resolution' && isDateRangeValid,
        startDate,
        endDate
    )
    const { data: resolutionDurationDistribution, isLoading: isResolutionDistributionLoading, refetch: refetchResolutionDistribution, isFetching: isFetchingResDist } = useTicketResolutionDurationDistribution(
        activeSection === 'resolution' && isDateRangeValid,
        startDate,
        endDate
    ) as {
        data: ResolutionDurationBucket[] | undefined
        isLoading: boolean
        refetch: () => void
        isFetching: boolean
    }
    const { data: resolutionTimesByWeek, refetch: refetchResolutionByWeek, isFetching: isFetchingResByWeek } = useResolutionTimesByWeek(
        activeSection === 'resolution' && isDateRangeValid,
        startDate,
        endDate
    )
    const { data: unresolvedTicketAges, refetch: refetchUnresolvedAges, isFetching: isFetchingUnresolvedAges } = useUnresolvedTicketAges(
        activeSection === 'resolution' && isDateRangeValid,
        startDate,
        endDate
    )
    const { data: incomingVsResolvedRate, refetch: refetchIncomingVsResolved, isFetching: isFetchingIncomingVsResolved } = useIncomingVsResolvedRate(
        activeSection === 'resolution' && isDateRangeValid,
        startDate,
        endDate
    )
    
    const { data: avgEscalationDurationByTag, refetch: refetchAvgEscDuration, isFetching: isFetchingAvgEsc } = useAvgEscalationDurationByTag(
        activeSection === 'escalation' && isDateRangeValid,
        startDate,
        endDate
    )
    const { data: escalationPercentageByTag, refetch: refetchEscPercentage, isFetching: isFetchingEscPerc } = useEscalationPercentageByTag(
        activeSection === 'escalation' && isDateRangeValid,
        startDate,
        endDate
    )
    const { data: escalationTrendsByDate, refetch: refetchEscTrends, isFetching: isFetchingEscTrends } = useEscalationTrendsByDate(
        activeSection === 'escalation' && isDateRangeValid,
        startDate,
        endDate
    )
    const { data: escalationsByTeam, refetch: refetchEscByTeam, isFetching: isFetchingEscTeam } = useEscalationsByTeam(
        activeSection === 'escalation' && isDateRangeValid,
        startDate,
        endDate
    )
    const { data: escalationsByImpact, refetch: refetchEscByImpact, isFetching: isFetchingEscImpact } = useEscalationsByImpact(
        activeSection === 'escalation' && isDateRangeValid,
        startDate,
        endDate
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
        startDate,
        endDate
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
                        <button
                            onClick={setLastWeek}
                            className={`px-3 py-1.5 text-xs font-medium rounded transition-all ${
                                dateFilterMode === 'week'
                                    ? 'bg-blue-600 text-white'
                                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                            }`}
                        >
                            Last 7 Days
                        </button>
                        <button
                            onClick={setLastMonth}
                            className={`px-3 py-1.5 text-xs font-medium rounded transition-all ${
                                dateFilterMode === 'month'
                                    ? 'bg-blue-600 text-white'
                                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                            }`}
                        >
                            Last Month
                        </button>
                        <button
                            onClick={setLastYear}
                            className={`px-3 py-1.5 text-xs font-medium rounded transition-all ${
                                dateFilterMode === 'year'
                                    ? 'bg-blue-600 text-white'
                                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                            }`}
                        >
                            Last Year
                        </button>
                        <button
                            onClick={handleCustomModeClick}
                            className={`px-3 py-1.5 text-xs font-medium rounded transition-all ${
                                dateFilterMode === 'custom'
                                    ? 'bg-blue-600 text-white'
                                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                            }`}
                        >
                            Custom
                        </button>
                        
                        {dateFilterMode === 'custom' && (
                            <>
                                <input
                                    type="date"
                                    value={startDate}
                                    onChange={(e) => setStartDate(e.target.value)}
                                    className="px-2 py-1 text-xs border border-gray-300 rounded focus:outline-none focus:ring-1 focus:ring-blue-500"
                                />
                                <span className="text-gray-400 text-xs">to</span>
                                <input
                                    type="date"
                                    value={endDate}
                                    onChange={(e) => setEndDate(e.target.value)}
                                    className="px-2 py-1 text-xs border border-gray-300 rounded focus:outline-none focus:ring-1 focus:ring-blue-500"
                                />
                            </>
                        )}
                        
                        <span className="text-xs text-gray-500 ml-2">
                            📅 {startDate} → {endDate}
                        </span>
                        
                        {!isDateRangeValid && (
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
                                    onClick={() => setActiveSection(section.key)}
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
