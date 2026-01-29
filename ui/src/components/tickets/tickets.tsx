'use client'

import {useEffect, useMemo, useState} from 'react'
import {useAllTickets, useRegistry, useTenantTeams, useTickets, useAssignmentEnabled} from '@/lib/hooks'
import {useTeamFilter} from '@/contexts/TeamFilterContext'
import {TicketWithLogs, PaginatedTickets, TicketImpact, TicketTag} from "@/lib/types"
import LoadingSkeleton from '@/components/LoadingSkeleton'
import EditTicketModal from './EditTicketModal'
import {useQueryClient} from '@tanstack/react-query'


export default function TicketsPage() {
    const {hasFullAccess, effectiveTeams} = useTeamFilter()
    const queryClient = useQueryClient()
    const {data: isAssignmentEnabled} = useAssignmentEnabled()
    type DateFilter = '' | 'lastWeek' | 'last2Weeks' | 'lastMonth' | 'custom'

    // Selected ticket
    const [selectedTicketId, setSelectedTicketId] = useState<string | null>(null)
    const [isModalOpen, setIsModalOpen] = useState(false)

    // Filters
    const [dateFilter, setDateFilter] = useState<DateFilter>('lastWeek')
    const [customDateRange, setCustomDateRange] = useState<{ start?: string; end?: string }>({})
    const [statusFilter, setStatusFilter] = useState('')
    const [teamFilter, setTeamFilter] = useState('')
    const [impactFilter, setImpactFilter] = useState('')
    const [tagFilter, setTagFilter] = useState('')
    const [escalatedFilter, setEscalatedFilter] = useState('')

    // Pagination
    const [currentPage, setCurrentPage] = useState(0)
    const pageSize = 15

    // Calculate date range based on filter
    // When switching to "custom", preserve the current range until custom dates are set
    const dateRange = useMemo(() => {
        if (!dateFilter) return { from: undefined, to: undefined }
        
        if (dateFilter === 'custom') {
            // If custom dates are not set yet, preserve the previous filter's range
            if (!customDateRange.start || !customDateRange.end) {
                // Calculate the current range based on last week (default)
                const now = new Date()
                const to = now.toISOString().split('T')[0]
                const fromDate = new Date(now)
                fromDate.setDate(now.getDate() - 7)
                const from = fromDate.toISOString().split('T')[0]
                return { from, to }
            }
            return {
                from: customDateRange.start,
                to: customDateRange.end
            }
        }
        
        const now = new Date()
        const to = now.toISOString().split('T')[0]
        const fromDate = new Date(now)
        
        switch (dateFilter) {
            case 'lastWeek':
                fromDate.setDate(now.getDate() - 7)
                break
            case 'last2Weeks':
                fromDate.setDate(now.getDate() - 14)
                break
            case 'lastMonth':
                fromDate.setDate(now.getDate() - 30)
                break
        }
        
        const from = fromDate.toISOString().split('T')[0]
        return { from, to }
    }, [dateFilter, customDateRange])

    const hasClientFilters = useMemo(
        () => !!(statusFilter || teamFilter || impactFilter || tagFilter || escalatedFilter),
        [statusFilter, teamFilter, impactFilter, tagFilter, escalatedFilter]
    )

    // Data hooks
    // - When filters are applied: pull all pages client-side (both superusers and regular users) to avoid missing matches on later pages.
    // - When no filters and superuser: use backend pagination (page/pageSize) for efficiency.
    // - When no filters and non-superuser: keep existing large single fetch (page 0, size 1000) and client-side paginate.
    const backendPageSize = hasFullAccess ? pageSize : 1000
    const backendPage = hasFullAccess ? currentPage : 0

    const shouldUseAllTickets = hasClientFilters
    const allTicketsQuery = useAllTickets(200, dateRange.from, dateRange.to, shouldUseAllTickets)
    const pagedTicketsQuery = useTickets(backendPage, backendPageSize, dateRange.from, dateRange.to)

    const ticketsData = shouldUseAllTickets ? allTicketsQuery.data : pagedTicketsQuery.data
    const ticketsLoading = shouldUseAllTickets ? allTicketsQuery.isLoading : pagedTicketsQuery.isLoading
    const ticketsError = shouldUseAllTickets ? allTicketsQuery.error : pagedTicketsQuery.error
    const {data: teamsData} = useTenantTeams()
    const {data: registryData} = useRegistry()

    const statusColors: Record<string, string> = {
        opened: 'bg-blue-100 text-blue-800',
        closed: 'bg-green-100 text-green-800',
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

    // --- Filter tickets based on superuser/team + UI filters ---
    // Date filtering is now done server-side
    const filteredTickets = useMemo(() => {
        if (!ticketsContent) return []

        // Step 1: filter by effective teams (considers team selector)
        const visibleTickets = hasFullAccess
            ? ticketsContent // Full access -> show all
            : effectiveTeams.length > 0
                ? ticketsContent.filter((t: TicketWithLogs) => t.team?.name && effectiveTeams.includes(t.team.name)) // Filter by teams
                : [] // No teams and no full access -> no tickets

        // Step 2: apply UI filters
        return visibleTickets.filter((t: TicketWithLogs) => {
            const matchesStatus = statusFilter ? t.status === statusFilter : true
            const matchesTeam = teamFilter ? t.team?.name === teamFilter : true
            const matchesImpact = impactFilter ? t.impact === impactFilter : true
            const matchesTag = tagFilter ? t.tags?.includes(tagFilter) : true
            const matchesEscalated = escalatedFilter
                ? escalatedFilter === 'Yes'
                    ? (t.escalations?.length ?? 0) > 0
                    : (t.escalations?.length ?? 0) === 0
                : true

            return matchesStatus && matchesTeam && matchesImpact && matchesTag && matchesEscalated
        })
    }, [ticketsContent, hasFullAccess, effectiveTeams, statusFilter, teamFilter, impactFilter, tagFilter, escalatedFilter])

    // Reset page when filters change
    useEffect(() => setCurrentPage(0), [statusFilter, teamFilter, impactFilter, tagFilter, escalatedFilter, dateFilter, customDateRange])

    // Client-side pagination for non-superusers
    const paginatedTickets = useMemo(() => {
        if (hasFullAccess) {
            // Superusers: already paginated by backend
            return filteredTickets
        } else {
            // Non-superusers: paginate client-side after filtering
            const start = currentPage * pageSize
            return filteredTickets.slice(start, start + pageSize)
        }
    }, [filteredTickets, currentPage, pageSize, hasFullAccess])

    // Calculate pagination info
    const totalPages = hasFullAccess 
        ? (ticketsDataTyped?.totalPages || 0) 
        : Math.ceil(filteredTickets.length / pageSize)

    // --- Render ---
    return (
        <div className="p-6 space-y-6">
            <h1 className="text-3xl font-bold text-gray-800">
                {hasFullAccess
                    ? 'Tickets Dashboard - All Teams'
                    : effectiveTeams.length > 0
                        ? `Tickets Dashboard - ${effectiveTeams.join(', ')}`
                        : 'Tickets Dashboard'}
            </h1>

            {/* Filters */}
            <div className={`grid grid-cols-1 gap-2 mb-4 ${hasFullAccess ? 'sm:grid-cols-6' : 'sm:grid-cols-5'}`}>
                {/* Date Filter - First */}
                <select value={dateFilter} onChange={e => setDateFilter(e.target.value as DateFilter)} className="p-2 border rounded">
                    <option value="">Any Date</option>
                    <option value="lastWeek">Last Week</option>
                    <option value="last2Weeks">Last 2 Weeks</option>
                    <option value="lastMonth">Last Month</option>
                    <option value="custom">Custom Range</option>
                </select>

                <select value={statusFilter} onChange={e => setStatusFilter(e.target.value)}
                        className="p-2 border rounded">
                    <option value="">All Status</option>
                    <option value="opened">Opened</option>
                    <option value="closed">Closed</option>
                    <option value="stale">Stale</option>
                </select>

                {hasFullAccess && (
                    <select value={teamFilter} onChange={e => setTeamFilter(e.target.value)} className="p-2 border rounded">
                        <option value="">All Teams</option>
                        {teamOptions.map((name: string, index: number) => (
                            <option key={`team-${index}-${name}`} value={name}>{name}</option>
                        ))}
                    </select>
                )}

                <select value={impactFilter} onChange={e => setImpactFilter(e.target.value)}
                        className="p-2 border rounded">
                    <option value="">All Impacts</option>
                    {registryData?.impacts.map((impact: TicketImpact) => <option key={impact.code} value={impact.code}>{impact.label}</option>)}
                </select>

                <select value={tagFilter} onChange={e => setTagFilter(e.target.value)} className="p-2 border rounded">
                    <option value="">All Tags</option>
                    {registryData?.tags.map((tag: TicketTag) => <option key={tag.code} value={tag.code}>{tag.label}</option>)}
                </select>

                <select value={escalatedFilter} onChange={e => setEscalatedFilter(e.target.value)}
                        className="p-2 border rounded">
                    <option value="">Escalated?</option>
                    <option value="Yes">Yes</option>
                    <option value="No">No</option>
                </select>

                {dateFilter === 'custom' && (
                    <>
                        <input type="date" value={customDateRange.start || ''}
                               onChange={e => setCustomDateRange({...customDateRange, start: e.target.value})}
                               className="p-2 border rounded"/>
                        <input type="date" value={customDateRange.end || ''}
                               onChange={e => setCustomDateRange({...customDateRange, end: e.target.value})}
                               className="p-2 border rounded"/>
                    </>
                )}
            </div>

            {/* Tickets Table */}
            <div className="overflow-x-auto border rounded shadow-sm mb-6">
                {ticketsLoading ? <LoadingSkeleton /> :
                    ticketsError ? (
                        <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-800 m-6">
                            <p className="font-semibold">Error loading tickets</p>
                            <p className="text-sm mt-1">Unable to load ticket data. Please try refreshing the page.</p>
                        </div>
                    ) :
                        <table className="min-w-full divide-y">
                            <thead className="bg-gray-50">
                            <tr>
                                <th className="px-4 py-3 text-left text-xs font-bold text-gray-700 uppercase tracking-wider">Status</th>
                                <th className="px-4 py-3 text-left text-xs font-bold text-gray-700 uppercase tracking-wider">Team</th>
                                <th className="px-4 py-3 text-left text-xs font-bold text-gray-700 uppercase tracking-wider">Impact</th>
                                {isAssignmentEnabled && (
                                    <th className="px-4 py-3 text-left text-xs font-bold text-gray-700 uppercase tracking-wider">Support Engineer</th>
                                )}
                                <th className="px-4 py-3 text-left text-xs font-bold text-gray-700 uppercase tracking-wider">Escalated</th>
                                <th className="px-4 py-3 text-left text-xs font-bold text-gray-700 uppercase tracking-wider">Opened At</th>
                                <th className="px-4 py-3 text-left text-xs font-bold text-gray-700 uppercase tracking-wider">Closed At</th>
                                <th className="px-4 py-3 text-left text-xs font-bold text-gray-700 uppercase tracking-wider">Link</th>
                            </tr>
                            </thead>
                            <tbody className="divide-y">
                            {paginatedTickets.map((t: TicketWithLogs) => {
                                const {opened, closed} = getOpenedClosed(t)
                                return (
                                    <tr key={t.id}
                                        onClick={() => {
                                            setSelectedTicketId(t.id)
                                            setIsModalOpen(true)
                                        }}
                                        className={`cursor-pointer transition-all hover:bg-blue-50 hover:shadow-md border-l-4 border-transparent hover:border-blue-400`}
                                    >
                                        <td className="px-4 py-4 whitespace-nowrap text-sm"><span
                                            className={`px-2 py-1 rounded-full text-xs font-semibold ${statusColors[t.status] || 'bg-gray-100 text-gray-800'}`}>{t.status}</span>
                                        </td>
                                        <td className="px-4 py-4 whitespace-nowrap text-sm text-gray-700">{t.team?.name || '-'}</td>
                                        <td className="px-4 py-4 whitespace-nowrap text-sm text-gray-700">{registryData?.impacts.find((i: TicketImpact) => i.code === t.impact)?.label || t.impact || '-'}</td>
                                        {isAssignmentEnabled && (
                                            <td className="px-4 py-4 whitespace-nowrap text-sm text-gray-700">{t.assignedTo || '-'}</td>
                                        )}
                                        <td className="px-4 py-4 whitespace-nowrap text-sm text-gray-700">{(t.escalations?.length ?? 0) > 0 ? 'Yes' : 'No'}</td>
                                        <td className="px-4 py-4 whitespace-nowrap text-sm text-gray-700">{opened ? new Date(opened).toLocaleString() : '-'}</td>
                                        <td className="px-4 py-4 whitespace-nowrap text-sm text-gray-700">{closed ? new Date(closed).toLocaleString() : '-'}</td>
                                        <td className="px-4 py-4 whitespace-nowrap text-sm">
                                            {t.query?.link ? (
                                                <a
                                                    href={t.query.link}
                                                    className="text-blue-600 hover:underline"
                                                    target="_blank"
                                                    rel="noopener noreferrer"
                                                >
                                                    View
                                                </a>
                                            ) : (
                                                'N/A'
                                            )}
                                        </td>
                                    </tr>
                                )
                            })}
                            {filteredTickets.length === 0 && <tr>
                                <td colSpan={isAssignmentEnabled ? 8 : 7} className="text-center py-4 text-gray-500">No tickets found</td>
                            </tr>}
                            </tbody>
                        </table>
                }
            </div>

            {/* Pagination Controls */}
            {totalPages > 1 && (
                <div className="flex justify-center items-center space-x-4 mt-4">
                    <button 
                        disabled={currentPage === 0} 
                        onClick={() => setCurrentPage(p => p - 1)}
                        className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600 disabled:bg-gray-300 disabled:cursor-not-allowed"
                    >
                        Previous
                    </button>
                    <div className="text-sm text-gray-600">
                        Page {currentPage + 1} of {totalPages} 
                        <span className="ml-2 text-gray-500">
                            ({paginatedTickets.length} 
                            {statusFilter || teamFilter || impactFilter || tagFilter || escalatedFilter || dateFilter ? ' matching' : ''} 
                            {' '}on this page)
                        </span>
                    </div>
                    <button 
                        disabled={currentPage >= totalPages - 1} 
                        onClick={() => setCurrentPage(p => p + 1)}
                        className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600 disabled:bg-gray-300 disabled:cursor-not-allowed"
                    >
                        Next
                    </button>
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
