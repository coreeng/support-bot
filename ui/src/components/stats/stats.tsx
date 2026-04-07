'use client'

import { type JSX, useEffect, useMemo } from 'react'
import { useAllTickets, useIncomingVsResolvedRate, useRegistry } from '@/lib/hooks'
import { TimeSeriesChart } from '@/components/dashboards/TimeSeriesChart'
import { HorizontalBarChart } from '@/components/dashboards/HorizontalBarChart'
import { useTeamFilter } from '@/contexts/TeamFilterContext'
import { type TicketImpact, type TicketWithLogs } from '@/lib/types'
import EscalatedToMyTeamWidget from '@/components/escalations/EscalatedToMyTeamWidget'
import { useAuth } from '@/hooks/useAuth'
import LoadingSkeleton from '@/components/LoadingSkeleton'
import { TEAM_SCOPE } from '@/lib/constants'
import type { IncomingVsResolvedRatePoint } from '@/lib/types/dashboard'
import { normalizeTeamKey } from '@/lib/teamUtils'
import { useUrlParams, enumValidator, isoDateValidator } from '@/lib/hooks/useUrlParams'
import { type DateFilter, getDateRangeFromFilter, PRESET_DAYS } from '@/lib/dateRange'
import { formatIncomingVsResolvedSeries } from '@/lib/incomingVsResolved'

const VALID_DATE_FILTERS = ['lastWeek', 'last2Weeks', 'lastMonth', 'lastYear', 'custom', 'all'] as const satisfies readonly DateFilter[]

type DateFilterControlsProps = {
    dateFilter: DateFilter
    dateFrom: string
    dateTo: string
    onDateFilterChange: (value: DateFilter) => void
    onDateFromChange: (value: string) => void
    onDateToChange: (value: string) => void
}

function getDashboardTitle(effectiveTeams: string[], hasNoTeamScope: boolean): string {
    if (hasNoTeamScope) {
        return 'Home Dashboard'
    }

    if (effectiveTeams.length === 0) {
        return 'Home Dashboard - All Teams'
    }

    return `Home Dashboard - ${effectiveTeams.join(', ')}`
}

const INCOMING_RESOLVED_LINES = [
    { dataKey: 'incoming', name: 'Incoming', color: '#ef4444' },
    { dataKey: 'resolved', name: 'Resolved', color: '#22c55e' },
]

function getIncomingResolvedTeamCodes(
    effectiveTeams: string[],
    userTeams: Array<{ name: string; label: string; code: string }> = []
): string[] {
    const teamCodeByScopeKey = new Map(
        userTeams.flatMap(team => [
            [normalizeTeamKey(team.name), team.code],
            [normalizeTeamKey(team.label), team.code],
            [normalizeTeamKey(team.code), team.code],
        ])
    )

    return effectiveTeams
        .filter(team => team !== TEAM_SCOPE.NO_TEAMS)
        .map(team => teamCodeByScopeKey.get(normalizeTeamKey(team)))
        .filter((team): team is string => !!team)
}

function renderIncomingResolvedChart({
    hasNoTeamScope,
    isLoading,
    error,
    data,
}: {
    hasNoTeamScope: boolean
    isLoading: boolean
    error: Error | null
    data: IncomingVsResolvedRatePoint[]
}): JSX.Element {
    const emptyMessage = hasNoTeamScope
        ? 'Incoming and resolved ticket activity cannot be shown without team access.'
        : 'No incoming or resolved ticket activity for the selected date range.'
    if (hasNoTeamScope) {
        return (
            <TimeSeriesChart
                title="Incoming vs Resolved"
                data={[]}
                lines={INCOMING_RESOLVED_LINES}
                xAxisDataKey="time"
                yAxisLabel="Tickets"
                tooltipFormatter={(value, name) => [`${value} ${value === 1 ? 'ticket' : 'tickets'}`, name]}
                height={280}
                showLegend={true}
                emptyMessage={emptyMessage}
            />
        )
    }

    if (error) {
        return (
            <div className="bg-white rounded-xl p-6 shadow-sm border border-red-200">
                <h2 className="text-lg font-semibold text-slate-900 mb-2">Incoming vs Resolved</h2>
                <p className="text-sm text-red-600">Unable to load incoming and resolved ticket activity.</p>
            </div>
        )
    }

    if (isLoading) {
        return (
            <div className="bg-white rounded-xl p-6 shadow-sm border border-slate-200">
                <h2 className="text-lg font-semibold text-slate-900 mb-2">Incoming vs Resolved</h2>
                <p className="text-sm text-slate-500">Loading incoming and resolved ticket activity...</p>
            </div>
        )
    }

    return (
        <TimeSeriesChart
            title="Incoming vs Resolved"
            data={data}
            lines={INCOMING_RESOLVED_LINES}
            xAxisDataKey="time"
            yAxisLabel="Tickets"
            tooltipFormatter={(value, name) => [`${value} ${value === 1 ? 'ticket' : 'tickets'}`, name]}
            height={280}
            showLegend={true}
            emptyMessage={emptyMessage}
        />
    )
}

function DateFilterControls({
    dateFilter,
    dateFrom,
    dateTo,
    onDateFilterChange,
    onDateFromChange,
    onDateToChange,
}: DateFilterControlsProps): JSX.Element {
    return (
        <div className="flex items-center gap-2">
            <select
                value={dateFilter}
                onChange={e => onDateFilterChange(e.target.value as DateFilter)}
                className="p-2 border rounded text-sm"
            >
                <option value="lastWeek">Last Week</option>
                <option value="last2Weeks">Last 2 Weeks</option>
                <option value="lastMonth">Last Month</option>
                <option value="lastYear">Last Year</option>
                <option value="custom">Custom Range</option>
                <option value="all">All Time</option>
            </select>
            {dateFilter === 'custom' && (
                <>
                    <input
                        type="date"
                        value={dateFrom}
                        onChange={e => onDateFromChange(e.target.value)}
                        className="p-2 border rounded text-sm"
                    />
                    <span className="text-gray-500">to</span>
                    <input
                        type="date"
                        value={dateTo}
                        onChange={e => onDateToChange(e.target.value)}
                        className="p-2 border rounded text-sm"
                    />
                </>
            )}
        </div>
    )
}

function filterTicketsByScope(
    tickets: TicketWithLogs[],
    effectiveTeams: string[],
    hasNoTeamScope: boolean
): TicketWithLogs[] {
    if (hasNoTeamScope) {
        return []
    }

    if (effectiveTeams.length === 0) {
        return tickets
    }

    return tickets.filter(ticket => {
        if (!ticket.team?.name) {
            return false
        }

        const ticketTeam = normalizeTeamKey(ticket.team.name)
        return effectiveTeams.some(team => normalizeTeamKey(team) === ticketTeam)
    })
}


export default function StatsPage() {
    // Persist date filter and custom date range in the URL.
    // Validators guard against invalid URL values and auto-correct the URL.
    const [params, setParams] = useUrlParams(
        { dateFilter: 'lastWeek', dateFrom: '', dateTo: '' },
        { dateFilter: enumValidator(VALID_DATE_FILTERS, 'lastWeek'), dateFrom: isoDateValidator, dateTo: isoDateValidator },
    )

    // Safe to cast: enumValidator guarantees params.dateFilter is a valid DateFilter.
    const dateFilter = params.dateFilter as DateFilter
    const isAllTime = dateFilter === 'all'

    // Correct the URL when custom date range is in an invalid order (dateFrom > dateTo).
    useEffect(() => {
        if (params.dateFilter === 'custom' && params.dateFrom && params.dateTo && params.dateFrom > params.dateTo) {
            setParams({ dateFilter: 'lastWeek', dateFrom: '', dateTo: '' })
        }
    }, [params.dateFilter, params.dateFrom, params.dateTo, setParams])

    // Calculate date range based on filter using the shared utility.
    // Falls back to lastWeek when custom dates are not yet set.
    const dateRange = useMemo(
        () =>
            getDateRangeFromFilter({
                dateFilter,
                customDateRange: { start: params.dateFrom || undefined, end: params.dateTo || undefined },
                customValue: 'custom',
                fallbackValue: 'lastWeek',
                allValue: 'all',
                presetDays: PRESET_DAYS,
            }),
        [dateFilter, params.dateFrom, params.dateTo]
    )

    // Use useAllTickets to fetch all tickets within date range (not just first 1000)
    // This ensures we get complete data for accurate statistics
    const { data: ticketsData, isLoading: isTicketsLoading, error: ticketsError } = useAllTickets(200, dateRange.from, dateRange.to)
    const { data: registryData } = useRegistry()
    const {
        effectiveTeams,
        hasNoTeamScope: contextHasNoTeamScope,
        selectedTeam,
        isViewingAsEscalationTeam: contextIsViewingAsEscalationTeam
    } = useTeamFilter()
    const { user, actualEscalationTeams } = useAuth()
    const hasNoTeamScope = contextHasNoTeamScope ?? effectiveTeams.includes(TEAM_SCOPE.NO_TEAMS)
    const isViewingAsEscalationTeam = contextIsViewingAsEscalationTeam ??
        (!!selectedTeam && actualEscalationTeams.includes(selectedTeam))
    const chartTeams = useMemo(
        () => getIncomingResolvedTeamCodes(effectiveTeams, user?.teams ?? []),
        [effectiveTeams, user?.teams]
    )
    const {
        data: incomingResolvedRate,
        isLoading: isIncomingResolvedLoading,
        error: incomingResolvedError,
    } = useIncomingVsResolvedRate(
        !hasNoTeamScope,
        isAllTime ? undefined : dateRange.from,
        dateRange.to,
        {
            teams: chartTeams,
            allTime: isAllTime,
            granularity: 'AUTO',
        }
    )
    const formattedIncomingResolvedSeries = useMemo(
        () => formatIncomingVsResolvedSeries(
            incomingResolvedRate?.data ?? [],
            incomingResolvedRate?.granularity
        ),
        [incomingResolvedRate]
    )

    const teamTickets = useMemo(
        () => filterTicketsByScope(ticketsData?.content ?? [], effectiveTeams, hasNoTeamScope),
        [ticketsData, effectiveTeams, hasNoTeamScope]
    )

    // Compute stats
    const totalTickets = teamTickets.length
    const openTickets = teamTickets.filter(t => t.status === 'opened').length
    const resolvedTickets = teamTickets.filter(t => t.status === 'closed').length
    const escalatedTickets = teamTickets.filter(t => (t.escalations?.length ?? 0) > 0).length

    // Tickets by Impact (excludes untagged tickets)
    const ticketsByImpact = useMemo(() => {
        const counts: Record<string, number> = {}
        teamTickets.forEach(t => {
            const impactLabel =
                registryData?.impacts.find((i: TicketImpact) => i.code === t.impact)?.label
            if (impactLabel) {
                counts[impactLabel] = (counts[impactLabel] || 0) + 1
            }
        })
        return Object.entries(counts).map(([name, value]) => ({ name, value }))
    }, [teamTickets, registryData])

    const incomingResolvedChart = renderIncomingResolvedChart({
        hasNoTeamScope,
        isLoading: isIncomingResolvedLoading,
        error: incomingResolvedError,
        data: formattedIncomingResolvedSeries
    })

    const handleDateFilterChange = (next: DateFilter) => {
        if (next === 'custom') {
            setParams({ dateFilter: next })
            return
        }

        setParams({ dateFilter: next, dateFrom: '', dateTo: '' })
    }

    const dateFilterControls = (
        <DateFilterControls
            dateFilter={dateFilter}
            dateFrom={params.dateFrom}
            dateTo={params.dateTo}
            onDateFilterChange={handleDateFilterChange}
            onDateFromChange={dateFrom => setParams({ dateFrom })}
            onDateToChange={dateTo => setParams({ dateTo })}
        />
    )

    const dashboardTitle = getDashboardTitle(effectiveTeams, hasNoTeamScope)

    const impactChart = (
        <HorizontalBarChart
            title="Tickets by Impact"
            data={ticketsByImpact}
            dataKey="value"
            yAxisDataKey="name"
            color="#6366f1"
            tooltipFormatter={value => [`${value} ${value === 1 ? 'ticket' : 'tickets'}`, '']}
            tooltipLabelFormatter={() => ''}
            tooltipSeparator=""
            height={280}
        />
    )

    if (isTicketsLoading) return <LoadingSkeleton />
    if (ticketsError) return (
        <div className="p-6">
            <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-800">
                <p className="font-semibold">Error loading dashboard</p>
                <p className="text-sm mt-1">Unable to load dashboard data. Please try refreshing the page.</p>
            </div>
        </div>
    )
    // Render split view for escalation teams
    if (isViewingAsEscalationTeam) {
        return (
            <div className="p-6 space-y-8">
                <div className="flex items-center justify-between">
                    <h1 className="text-3xl font-bold text-gray-800">
                        {`Home Dashboard - ${selectedTeam}`}
                    </h1>
                    {dateFilterControls}
                </div>

                {/* Section 1: Escalations We Are Handling */}
                <div className="border-2 border-purple-300 rounded-lg bg-gradient-to-br from-purple-50 to-indigo-50 p-6 shadow-lg">
                    <h2 className="text-xl font-bold text-purple-900 mb-6">Escalations We Are Handling</h2>
                    <EscalatedToMyTeamWidget />
                </div>

                {/* Section 2: Tickets We Own */}
                <div className="border-2 border-slate-300 rounded-lg bg-gradient-to-br from-slate-50 to-gray-50 p-6 shadow-lg">
                    <h2 className="text-xl font-bold text-slate-900 mb-6">Tickets We Own</h2>

                    {/* Summary cards */}
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-6">
                        <div className="p-6 bg-white shadow-md rounded-lg border border-gray-200">
                            <h3 className="font-semibold text-gray-700">Total Tickets</h3>
                            <p className="text-2xl font-bold text-blue-600 mt-2">{totalTickets}</p>
                        </div>
                        <div className="p-6 bg-white shadow-md rounded-lg border border-gray-200">
                            <h3 className="font-semibold text-gray-700">Open Tickets</h3>
                            <p className="text-2xl font-bold text-yellow-600 mt-2">{openTickets}</p>
                        </div>
                        <div className="p-6 bg-white shadow-md rounded-lg border border-gray-200">
                            <h3 className="font-semibold text-gray-700">Escalated Tickets</h3>
                            <p className="text-2xl font-bold text-red-600 mt-2">{escalatedTickets}</p>
                        </div>
                        <div className="p-6 bg-white shadow-md rounded-lg border border-gray-200">
                            <h3 className="font-semibold text-gray-700">Resolved Tickets</h3>
                            <p className="text-2xl font-bold text-green-600 mt-2">{resolvedTickets}</p>
                        </div>
                    </div>

                    {/* Charts */}
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                        {incomingResolvedChart}
                        {impactChart}
                    </div>
                </div>
            </div>
        )
    }

    // Default view for non-escalation teams
    return (
        <div className="p-6 space-y-6 relative">
            <div className="flex items-center justify-between">
                <h1 className="text-3xl font-bold text-gray-800">{dashboardTitle}</h1>
                {dateFilterControls}
            </div>

            {hasNoTeamScope && (
                <div className="bg-amber-50 border border-amber-200 rounded-lg p-4 text-amber-900">
                    <p className="font-semibold">No Team Access</p>
                    <p className="text-sm mt-1">You are not assigned to any teams, so dashboard data cannot be displayed.</p>
                </div>
            )}

            {/* Summary cards */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
                <div className="p-6 bg-white shadow-md rounded-lg border border-gray-200">
                    <h2 className="font-semibold text-gray-700">Total Tickets</h2>
                    <p className="text-2xl font-bold text-blue-600 mt-2">{totalTickets}</p>
                </div>
                <div className="p-6 bg-white shadow-md rounded-lg border border-gray-200">
                    <h2 className="font-semibold text-gray-700">Open Tickets</h2>
                    <p className="text-2xl font-bold text-yellow-600 mt-2">{openTickets}</p>
                </div>
                <div className="p-6 bg-white shadow-md rounded-lg border border-gray-200">
                    <h2 className="font-semibold text-gray-700">Escalated Tickets</h2>
                    <p className="text-2xl font-bold text-amber-500 mt-2">{escalatedTickets}</p>
                </div>
                <div className="p-6 bg-white shadow-md rounded-lg border border-gray-200">
                    <h2 className="font-semibold text-gray-700">Resolved Tickets</h2>
                    <p className="text-2xl font-bold text-green-600 mt-2">{resolvedTickets}</p>
                </div>
            </div>

            {/* Charts */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                {incomingResolvedChart}
                {impactChart}
            </div>
        </div>
    )
}
