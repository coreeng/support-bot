'use client'

import { useEffect, useMemo } from 'react'
import { useUrlParams, enumValidator, isoDateValidator } from '@/lib/hooks/useUrlParams'
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
import { Input } from '@/components/ui/input'
import {
 Select,
 SelectContent,
 SelectItem,
 SelectTrigger,
 SelectValue,
} from '@/components/ui/select'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs'

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
 dateFrom: isoDateValidator,
 dateTo: isoDateValidator,
 },
 )

 // Safe to cast: validators guarantee these are valid enum values.
 const activeSection = params.section as SectionKey
 const dateFilter = params.dateFilter as 'lastWeek' | 'last2Weeks' | 'lastMonth' | 'lastYear' | 'custom'

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
 dateRange.to,
 { granularity: 'AUTO' }
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
 <div className="space-y-6">
 <div className="flex items-start justify-between gap-4">
 <div>
 <h1 className="text-2xl font-bold text-foreground">SLA Dashboards</h1>
 <p className="text-muted-foreground text-sm">Performance at a glance</p>
 </div>
 <div className="flex flex-wrap items-center gap-2">
 <Select
 value={dateFilter}
 onValueChange={(v) => {
 if (v !== 'custom') {
 setParams({ dateFilter: v, dateFrom: '', dateTo: '' })
 } else {
 setParams({
 dateFilter: 'custom',
 dateFrom: params.dateFrom || dateRange.from || '',
 dateTo: params.dateTo || dateRange.to || '',
 })
 }
 }}
 >
 <SelectTrigger className="w-[160px]" data-testid="sla-date-filter"><SelectValue /></SelectTrigger>
 <SelectContent>
 <SelectItem value="lastWeek">Last Week</SelectItem>
 <SelectItem value="last2Weeks">Last 2 Weeks</SelectItem>
 <SelectItem value="lastMonth">Last Month</SelectItem>
 <SelectItem value="lastYear">Last Year</SelectItem>
 <SelectItem value="custom">Custom</SelectItem>
 </SelectContent>
 </Select>
 {dateFilter === 'custom' && (
 <>
 <Input type="date" value={params.dateFrom}
 onChange={e => setParams({ dateFrom: e.target.value })}
 className="w-[150px]"/>
 <Input type="date" value={params.dateTo}
 onChange={e => setParams({ dateTo: e.target.value })}
 className="w-[150px]"/>
 </>
 )}
 {dateFilter === 'custom' && !isDateRangeValid && (
 <span className="text-xs text-destructive font-medium">Invalid range</span>
 )}
 </div>
 </div>

 <Tabs value={activeSection} onValueChange={(v) => setParams({ section: v })} className="space-y-4">
 <TabsList>
 {sections.map((section) => {
 const Icon = section.icon
 return (
 <TabsTrigger key={section.key} value={section.key} className="cursor-pointer">
 <Icon className="h-4 w-4" />
 {section.label}
 </TabsTrigger>
 )
 })}
 </TabsList>

 <TabsContent value={activeSection} className="space-y-6">
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
 </TabsContent>
 </Tabs>
 </div>
 )
}
