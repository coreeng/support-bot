'use client'

import {useEffect, useMemo, useRef, useState} from 'react'
import {useAllTickets, useAssignmentEnabled, useRegistry, useTenantTeams, useTickets} from '@/lib/hooks'
import {useTeamFilter} from '@/contexts/TeamFilterContext'
import {PaginatedTickets, TicketImpact, TicketTag, TicketWithLogs} from "@/lib/types"
import LoadingSkeleton from '@/components/LoadingSkeleton'
import EditTicketModal from './EditTicketModal'
import {useQueryClient} from '@tanstack/react-query'
import {TEAM_SCOPE} from '@/lib/constants'
import {normalizeTeamKey} from '@/lib/teamUtils'
import {getDateRangeFromFilter, PRESET_DAYS} from '@/lib/dateRange'
import { enumValidator, isoDateValidator, nonNegativeIntValidator, useUrlParams } from '@/lib/hooks/useUrlParams'
import { ArrowDown, ArrowUp, ArrowUpDown } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from '@/components/ui/table'
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from '@/components/ui/select'
import { Input } from '@/components/ui/input'
import { SingleSelectFilter } from '@/components/ui/single-select-filter'


export default function TicketsPage() {
    const {
        effectiveTeams,
        hasNoTeamScope: contextHasNoTeamScope,
        isViewingAllTeams: contextIsViewingAllTeams,
        selectedTeam: teamFilterSelectedTeam
    } = useTeamFilter()
    const queryClient = useQueryClient()
    const {data: isAssignmentEnabled} = useAssignmentEnabled()
    const SUMMARY_COLUMN_WIDTH = '16rem'
    const BASE_COLUMN_COUNT = 9
    const columnCount = isAssignmentEnabled ? BASE_COLUMN_COUNT + 1 : BASE_COLUMN_COUNT
    const hasNoTeamScope = contextHasNoTeamScope ?? effectiveTeams.includes(TEAM_SCOPE.NO_TEAMS)
    const isViewingAllTeams = contextIsViewingAllTeams ?? (effectiveTeams.length === 0 && !hasNoTeamScope)
    type TicketDateFilter = '' | 'lastWeek' | 'last2Weeks' | 'lastMonth' | 'custom'
    type SortColumn = 'openedAt' | 'closedAt'

    // Selected ticket (UI-only — not persisted in the URL)
    const [selectedTicketId, setSelectedTicketId] = useState<string | null>(null)
    const [isModalOpen, setIsModalOpen] = useState(false)

    const {data: registryData} = useRegistry()
    const {data: teamsData} = useTenantTeams()

  // Persist all filter / sort / page controls in the URL.
    // Validators guard against invalid URL values and auto-correct the URL.
    const ALL_TEAMS_FILTER = TEAM_SCOPE.ALL_TEAMS
    const [params, setParams] = useUrlParams(
        {
            dateFilter: 'lastWeek',
            dateFrom: '',
            dateTo: '',
            status: '',
            teamFilter: '',
            impact: '',
            tag: '',
            escalated: '',
            escalatedTo: '',
            sortBy: 'openedAt',
            sortDir: 'desc',
            page: '0',
        },
        {
            dateFilter: enumValidator(['', 'lastWeek', 'last2Weeks', 'lastMonth', 'custom'] as const, 'lastWeek'),
            dateFrom: isoDateValidator,
            dateTo: isoDateValidator,
            status: enumValidator(['', 'opened', 'closed', 'stale'] as const, ''),
            escalated: enumValidator(['', 'Yes', 'No'] as const, ''),
            sortBy: enumValidator(['openedAt', 'closedAt'] as const, 'openedAt'),
            sortDir: enumValidator(['asc', 'desc'] as const, 'desc'),
            page: nonNegativeIntValidator,
            // impact and tag are intentionally unvalidated: their valid values come from
            // registryData which loads asynchronously after mount.  useUrlParams captures
            // validators once at mount time (via useState), so passing registry-dependent
            // validators would always see undefined and immediately wipe any URL value.
        },
    )

    // Type assertions are safe for dateFilter, status, escalated, sortBy, and sortDir —
    // each has an enumValidator above. page uses nonNegativeIntValidator and is parsed via parseInt
    // teamFilter, impact, tag, and escalatedTo have no validators and carry raw URL strings.
    const dateFilter    = params.dateFilter as TicketDateFilter
    const statusFilter  = params.status
    const teamFilter    = params.teamFilter
    const impactFilter  = params.impact
    const tagFilter     = params.tag
    const escalatedFilter    = params.escalated
    const escalatedToFilter  = params.escalatedTo
    const sortColumn: SortColumn       = params.sortBy as SortColumn
    const sortDirection: 'asc' | 'desc' = params.sortDir as 'asc' | 'desc'
    const unvalidatedCurrentPage = parseInt(params.page, 10)
    const pageSize = 15

    // Correct the URL when custom date range is in an invalid order (dateFrom > dateTo).
    useEffect(() => {
        if (params.dateFilter === 'custom' && params.dateFrom && params.dateTo && params.dateFrom > params.dateTo) {
            setParams({ dateFilter: 'lastWeek', dateFrom: '', dateTo: '' })
        }
    }, [params.dateFilter, params.dateFrom, params.dateTo, setParams])

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
                },
            }),
        [dateFilter, params.dateFrom, params.dateTo]
    )

  // Data hooks
    // - When filters are applied: pull all pages client-side to avoid missing matches on later pages.
    // - When viewing all teams (no team filter): use backend pagination for efficiency.
    // - When viewing a specific team: larger single fetch + client-side paginate.
    const shouldUseAllTickets = useMemo(
      () => !!(params.status || params.teamFilter || params.impact || params.tag || params.escalated || params.escalatedTo),
      [params.status, params.teamFilter, params.impact, params.tag, params.escalated, params.escalatedTo]
    )
    const useServerPagination = isViewingAllTeams && !shouldUseAllTickets
    const backendPageSize = useServerPagination ? pageSize : 1000
    const backendPage = useServerPagination ? unvalidatedCurrentPage : 0
    const allTicketsQuery = useAllTickets(200, dateRange.from, dateRange.to, shouldUseAllTickets)
    const pagedTicketsQuery = useTickets(backendPage, backendPageSize, dateRange.from, dateRange.to)

    const ticketsData = shouldUseAllTickets ? allTicketsQuery.data : pagedTicketsQuery.data
    const ticketsLoading = shouldUseAllTickets ? allTicketsQuery.isLoading : pagedTicketsQuery.isLoading
    const ticketsError = shouldUseAllTickets ? allTicketsQuery.error : pagedTicketsQuery.error

    const statusColors: Record<string, string> = {
        opened: 'bg-info/10 text-info',
        closed: 'bg-success/10 text-success',
    }
    // --- Utility functions ---
    const getOpenedClosed = (ticket: TicketWithLogs) => {
        if (!ticket.logs?.length) return {opened: null, closed: null}

        const sorted = [...ticket.logs].sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime())

        const opened = sorted[0].date

        const closedLog = [...sorted].reverse().find(log =>
            log.event.toLowerCase().includes('closed')
        )

        const closed = closedLog?.date || null

        return {opened, closed}
    }

    const getTagLabel = (tag: unknown): string => {
        if (typeof tag === 'string') return tag
        if (tag && typeof tag === 'object') {
            const obj = tag as { label?: string; code?: string }
            return obj.label || obj.code || ''
        }
        return ''
    }

    // Build team options from both registry list and the data we actually received.
    // This prevents missing teams (e.g., escalation/legacy teams) from showing 0 results when filtered.
    const ticketsDataTyped = ticketsData as PaginatedTickets | undefined
    const ticketsContent = useMemo(
        () => (ticketsDataTyped?.content as TicketWithLogs[] | undefined) ?? [],
        [ticketsDataTyped]
    )

    const teamOptions = useMemo(() => {
        const fromData = ticketsContent
            .map((t: TicketWithLogs) => t.team?.name)
            .filter((name): name is string => !!name)
        const fromRegistry = teamsData?.map((t: { name: string }) => t.name).filter(Boolean) ?? []
        return Array.from(new Set([...fromData, ...fromRegistry])).sort()
    }, [ticketsContent, teamsData])

    const escalatedToOptions = useMemo(() => {
        const escalationTeams = ticketsContent.flatMap((t: TicketWithLogs) =>
            (t.escalations ?? [])
                .map(e => e.team?.name)
                .filter((name): name is string => !!name)
        )
        return Array.from(new Set(escalationTeams)).sort()
    }, [ticketsContent])

    // --- Filter tickets based on superuser/team + UI filters ---
    // Date filtering is now done server-side
    const filteredTickets = useMemo(() => {
        if (!ticketsContent) return []

        if (hasNoTeamScope) {
            return []
        }

        // Step 1: filter by effective teams (considers team selector).
        // ALL three branches produce a correctly scoped subset so that Step 2
        // only needs to apply the remaining UI filters (status, impact, tag, etc.)
        // on an already-reduced dataset.
        const visibleTickets = teamFilter === ALL_TEAMS_FILTER
            ? ticketsContent // explicit page-level override: show all teams
            : teamFilter
                ? ticketsContent.filter((t: TicketWithLogs) => {  // explicit page-level team selection
                    if (!t.team?.name) return false
                    return normalizeTeamKey(t.team.name) === normalizeTeamKey(teamFilter)
                })
                : effectiveTeams.length === 0
                    ? ticketsContent // role-team view -> show all
                    : ticketsContent.filter((t: TicketWithLogs) => {
                        if (!t.team?.name) return false
                        const ticketTeam = normalizeTeamKey(t.team.name)
                        return effectiveTeams.some(team => normalizeTeamKey(team) === ticketTeam)
                    })

        // Step 2: apply remaining UI filters.
        // Team filtering is already handled by Step 1 — matchesTeam is not needed here.
        return visibleTickets.filter((t: TicketWithLogs) => {
            const matchesStatus = statusFilter ? t.status === statusFilter : true
            const matchesImpact = impactFilter ? t.impact === impactFilter : true
            const matchesTag = tagFilter ? t.tags?.includes(tagFilter) : true
            const matchesEscalated = escalatedFilter
                ? escalatedFilter === 'Yes'
                    ? (t.escalations?.length ?? 0) > 0
                    : (t.escalations?.length ?? 0) === 0
                : true
            const matchesEscalatedTo = escalatedToFilter
                ? (t.escalations ?? []).some(e => e.team?.name === escalatedToFilter)
                : true

            return matchesStatus && matchesImpact && matchesTag && matchesEscalated && matchesEscalatedTo
        })
    }, [ticketsContent, hasNoTeamScope, effectiveTeams, statusFilter, teamFilter, impactFilter, tagFilter, escalatedFilter, escalatedToFilter, ALL_TEAMS_FILTER])

    const sortedTickets = useMemo(() => {
        const toTs = (value: string | null) => (value ? new Date(value).getTime() : null)
        return [...filteredTickets].sort((a, b) => {
            const aDates = getOpenedClosed(a)
            const bDates = getOpenedClosed(b)
            const aValue = sortColumn === 'openedAt' ? toTs(aDates.opened) : toTs(aDates.closed)
            const bValue = sortColumn === 'openedAt' ? toTs(bDates.opened) : toTs(bDates.closed)

            // Keep missing dates last regardless of sort direction
            if (aValue === null && bValue === null) return 0
            if (aValue === null) return 1
            if (bValue === null) return -1

            const cmp = aValue - bValue
            return sortDirection === 'asc' ? cmp : -cmp
        })
    }, [filteredTickets, sortColumn, sortDirection])

    // Calculate pagination info
    const totalPages = useServerPagination
      ? (ticketsDataTyped?.totalPages || 0)
      : Math.ceil(sortedTickets.length / pageSize)
    const currentPage = Math.min(unvalidatedCurrentPage, Math.max(0, totalPages -1))

  // Reset page to 0 whenever any filter param changes.
    // useEffect is required here because setParams calls router.replace (a side effect)
    // and side effects must not be triggered during render.
    const filterKey = [
        params.status, params.teamFilter, params.impact, params.tag,
        params.escalated, params.escalatedTo, params.dateFilter, params.dateFrom, params.dateTo,
    ].join('|')
    const prevFilterKeyRef = useRef<string | undefined>(undefined)
    useEffect(() => {
        if (prevFilterKeyRef.current !== undefined && prevFilterKeyRef.current !== filterKey) {
            // Only write page=0 when the page isn't already 0.
            // Skipping the setParams call when currentPage===0 avoids a redundant
            // router.replace (the filter handler already called one), which would
            // otherwise cause an address-bar flicker on every filter change.
            if (currentPage !== 0) setParams({ page: '0' })
        }
        prevFilterKeyRef.current = filterKey
    }, [filterKey, setParams, currentPage])

    // Reset the page-level team filter when the sidebar "View as" scope changes.
    // The ref starts as `undefined` (sentinel for "not yet seen") so that the
    // initial context hydration sequence (null → firstTeam, driven by TeamSelector
    // reading ?team from the URL) is never mistaken for a user-initiated switch.
    const prevSelectedTeamRef = useRef<string | null | undefined>(undefined)
    useEffect(() => {
        const prev = prevSelectedTeamRef.current
        prevSelectedTeamRef.current = teamFilterSelectedTeam
        // Skip: first run (undefined) and context hydration (null → firstTeam).
        // Only reset when transitioning between two known, non-null team values.
        if (!prev || prev === teamFilterSelectedTeam) return
        setParams({ teamFilter: '', page: '0' })
    }, [teamFilterSelectedTeam, setParams])

    // Server pagination for all-teams without client filters; otherwise client-side pagination.
    const paginatedTickets = useMemo(() => {
        if (useServerPagination) {
            return sortedTickets
        }
        const start = currentPage * pageSize
        return sortedTickets.slice(start, start + pageSize)
    }, [sortedTickets, currentPage, pageSize, useServerPagination])

    const totalTickets = useServerPagination
        ? (ticketsDataTyped?.totalElements || 0)
        : sortedTickets.length

    // shadcn Select needs non-empty values; "" means "any" sentinel.
    const ANY = '__any'
    const fromAny = (v: string) => (v === ANY ? '' : v)
    const toAny = (v: string) => (v === '' ? ANY : v)

    // --- Render ---
    return (
        <div className="space-y-6">
            <div className="flex items-start justify-between gap-4">
                <div>
                    <h1 className="text-2xl font-bold text-foreground">Tickets</h1>
                    <p className="text-muted-foreground text-sm">Browse, filter, and update support tickets</p>
                </div>
                <div className="flex items-center gap-2">
                    <Select value={toAny(dateFilter)} onValueChange={(v) => {
                        const next = fromAny(v) as TicketDateFilter
                        setParams(next !== 'custom'
                            ? { dateFilter: next, dateFrom: '', dateTo: '' }
                            : { dateFilter: next })
                    }}>
                        <SelectTrigger className="w-[160px]"><SelectValue /></SelectTrigger>
                        <SelectContent>
                            <SelectItem value={ANY}>Any Date</SelectItem>
                            <SelectItem value="lastWeek">Last Week</SelectItem>
                            <SelectItem value="last2Weeks">Last 2 Weeks</SelectItem>
                            <SelectItem value="lastMonth">Last Month</SelectItem>
                            <SelectItem value="custom">Custom Range</SelectItem>
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
                </div>
            </div>

            {hasNoTeamScope && (
                <div className="rounded-lg border border-warning/30 bg-warning/10 p-4 text-warning">
                    <p className="font-semibold">No Team Access</p>
                    <p className="text-sm mt-1">You are not assigned to any teams, so tickets cannot be displayed.</p>
                </div>
            )}

            {/* Faceted filters — single-value, modeled on elevate's data-table-faceted-filter */}
            <div className="flex flex-wrap items-center gap-2">
                <SingleSelectFilter
                    title="Status"
                    value={statusFilter || undefined}
                    onChange={(v) => setParams({ status: v ?? '' })}
                    options={[
                        { label: 'Opened', value: 'opened' },
                        { label: 'Closed', value: 'closed' },
                        { label: 'Stale', value: 'stale' },
                    ]}
                />
                {!hasNoTeamScope && (
                    <SingleSelectFilter
                        title="Team"
                        value={teamFilter || undefined}
                        onChange={(v) => setParams({ teamFilter: v ?? '' })}
                        options={[
                            ...(!isViewingAllTeams ? [{ label: 'All Teams', value: ALL_TEAMS_FILTER }] : []),
                            ...teamOptions.map((name: string) => ({ label: name, value: name })),
                        ]}
                    />
                )}
                <SingleSelectFilter
                    title="Impact"
                    value={impactFilter || undefined}
                    onChange={(v) => setParams({ impact: v ?? '' })}
                    options={(registryData?.impacts ?? []).map((impact: TicketImpact) => ({
                        label: impact.label, value: impact.code,
                    }))}
                />
                <SingleSelectFilter
                    title="Tag"
                    value={tagFilter || undefined}
                    onChange={(v) => setParams({ tag: v ?? '' })}
                    options={(registryData?.tags ?? []).map((tag: TicketTag) => ({
                        label: tag.label, value: tag.code,
                    }))}
                />
                <SingleSelectFilter
                    title="Escalated"
                    value={escalatedFilter || undefined}
                    onChange={(v) => setParams({ escalated: v ?? '' })}
                    showSearch={false}
                    options={[
                        { label: 'Yes', value: 'Yes' },
                        { label: 'No', value: 'No' },
                    ]}
                />
                <SingleSelectFilter
                    title="Escalated To"
                    value={escalatedToFilter || undefined}
                    onChange={(v) => setParams({ escalatedTo: v ?? '' })}
                    options={escalatedToOptions.map((name: string) => ({ label: name, value: name }))}
                />
            </div>

            {/* Tickets Table */}
            <div className="border rounded-lg overflow-hidden">
                {ticketsLoading ? <LoadingSkeleton /> :
                    ticketsError ? (
                        <div className="m-6 rounded-lg border border-destructive/30 bg-destructive/10 p-4 text-destructive">
                            <p className="font-semibold">Error loading tickets</p>
                            <p className="text-sm mt-1">Unable to load ticket data. Please try refreshing the page.</p>
                        </div>
                    ) :
                        <Table>
                            <TableHeader className="bg-muted z-10">
                                <TableRow>
                                    <TableHead>Status</TableHead>
                                    <TableHead style={{ width: SUMMARY_COLUMN_WIDTH }}>Summary</TableHead>
                                    <TableHead>Team</TableHead>
                                    <TableHead>Impact</TableHead>
                                    <TableHead>Tags</TableHead>
                                    {isAssignmentEnabled && (
                                        <TableHead>Support Engineer</TableHead>
                                    )}
                                    <TableHead>Escalated</TableHead>
                                    <TableHead>Escalated To</TableHead>
                                    <TableHead
                                        className="cursor-pointer select-none"
                                        onClick={() => {
                                            if (sortColumn === 'openedAt') {
                                                setParams({ sortDir: sortDirection === 'asc' ? 'desc' : 'asc', page: '0' })
                                            } else {
                                                setParams({ sortBy: 'openedAt', sortDir: 'desc', page: '0' })
                                            }
                                        }}
                                    >
                                        <span className="inline-flex items-center gap-1">
                                            Opened At
                                            {sortColumn === 'openedAt'
                                                ? (sortDirection === 'asc' ? <ArrowUp className="h-3.5 w-3.5" /> : <ArrowDown className="h-3.5 w-3.5" />)
                                                : <ArrowUpDown className="h-3.5 w-3.5 text-muted-foreground" />}
                                        </span>
                                    </TableHead>
                                    <TableHead
                                        className="cursor-pointer select-none"
                                        onClick={() => {
                                            if (sortColumn === 'closedAt') {
                                                setParams({ sortDir: sortDirection === 'asc' ? 'desc' : 'asc', page: '0' })
                                            } else {
                                                setParams({ sortBy: 'closedAt', sortDir: 'desc', page: '0' })
                                            }
                                        }}
                                    >
                                        <span className="inline-flex items-center gap-1">
                                            Closed At
                                            {sortColumn === 'closedAt'
                                                ? (sortDirection === 'asc' ? <ArrowUp className="h-3.5 w-3.5" /> : <ArrowDown className="h-3.5 w-3.5" />)
                                                : <ArrowUpDown className="h-3.5 w-3.5 text-muted-foreground" />}
                                        </span>
                                    </TableHead>
                                </TableRow>
                            </TableHeader>
                            <TableBody>
                                {paginatedTickets.length === 0 ? (
                                    <TableRow>
                                        <TableCell colSpan={columnCount} className="text-center text-muted-foreground py-8">
                                            No tickets found
                                        </TableCell>
                                    </TableRow>
                                ) : paginatedTickets.map((t: TicketWithLogs) => {
                                    const {opened, closed} = getOpenedClosed(t)
                                    const escalatedTo = Array.from(
                                        new Set(
                                            (t.escalations ?? [])
                                                .map(e => e.team?.name)
                                                .filter((name): name is string => !!name)
                                        )
                                    )
                                    return (
                                        <TableRow
                                            key={t.id}
                                            onClick={() => {
                                                setSelectedTicketId(t.id)
                                                setIsModalOpen(true)
                                            }}
                                            className="cursor-pointer hover:bg-accent"
                                        >
                                            <TableCell>
                                                <span className={`px-2 py-1 rounded-full text-xs font-semibold ${statusColors[t.status] || 'bg-muted text-muted-foreground'}`}>
                                                    {t.status}
                                                </span>
                                            </TableCell>
                                            <TableCell
                                                className="whitespace-normal break-words align-top"
                                                style={{ width: SUMMARY_COLUMN_WIDTH, minWidth: SUMMARY_COLUMN_WIDTH }}
                                            >
                                                <div
                                                    className="overflow-hidden"
                                                    style={{
                                                        display: '-webkit-box',
                                                        WebkitLineClamp: 4,
                                                        WebkitBoxOrient: 'vertical',
                                                    }}
                                                >
                                                    {t.summary?.trim() ? t.summary : '—'}
                                                </div>
                                            </TableCell>
                                            <TableCell>{t.team?.name || '-'}</TableCell>
                                            <TableCell>{registryData?.impacts.find((i: TicketImpact) => i.code === t.impact)?.label || t.impact || '-'}</TableCell>
                                            <TableCell>
                                                {(t.tags?.length ?? 0) > 0
                                                    ? t.tags
                                                        ?.map(getTagLabel)
                                                        .filter(Boolean)
                                                        .map((tag, idx) => (
                                                            <Badge key={`${t.id}-tag-${idx}`} variant="outline" className="mb-1 last:mb-0 block w-fit">
                                                                {tag}
                                                            </Badge>
                                                        ))
                                                    : '-'}
                                            </TableCell>
                                            {isAssignmentEnabled && (
                                                <TableCell>{t.assignedTo || '-'}</TableCell>
                                            )}
                                            <TableCell>{(t.escalations?.length ?? 0) > 0 ? 'Yes' : 'No'}</TableCell>
                                            <TableCell>{escalatedTo.length ? escalatedTo.join(', ') : '-'}</TableCell>
                                            <TableCell>{opened ? new Date(opened).toLocaleString() : '-'}</TableCell>
                                            <TableCell>{closed ? new Date(closed).toLocaleString() : '-'}</TableCell>
                                        </TableRow>
                                    )
                                })}
                            </TableBody>
                        </Table>
                }
            </div>

            {/* Pagination Controls */}
            {totalPages > 1 && (
                <div className="flex justify-center items-center space-x-4">
                    <Button
                        variant="outline"
                        disabled={currentPage === 0}
                        onClick={() => setParams({ page: String(currentPage - 1) })}
                    >
                        Previous
                    </Button>
                    <div className="text-sm text-muted-foreground">
                        Page {currentPage + 1} of {totalPages}
                        <span className="ml-2">
                            ({paginatedTickets.length}
                            {params.status || params.teamFilter || params.impact || params.tag || params.escalated || params.escalatedTo || params.dateFilter ? ' matching' : ''}
                            {' '}on this page, {totalTickets} total)
                        </span>
                    </div>
                    <Button
                        variant="outline"
                        disabled={currentPage >= totalPages - 1}
                        onClick={() => setParams({ page: String(currentPage + 1) })}
                    >
                        Next
                    </Button>
                </div>
            )}

            {/* Edit Ticket Modal */}
            <EditTicketModal
                ticketId={selectedTicketId}
                open={isModalOpen}
                onOpenChange={(open) => {
                    setIsModalOpen(open)
                    if (!open) {
                        setSelectedTicketId(null)
                    }
                }}
                onSuccess={() => {
                    // Invalidate queries to refresh ticket data
                    queryClient.invalidateQueries({ queryKey: ['tickets'] })
                    queryClient.invalidateQueries({ queryKey: ['ticket', selectedTicketId] })
                }}
            />
        </div>
    )
}
