'use client'

import { useState, useMemo } from 'react'
import { useEscalations, useEscalationTeams, useRegistry } from '@/lib/hooks'
import { useTeamFilter } from '@/contexts/TeamFilterContext'
import { useAuth } from '@/hooks/useAuth'
import { useNow } from '@/hooks/useNow'
import EscalatedToMyTeamTable from './EscalatedToMyTeamTable'
import LoadingSkeleton from '@/components/LoadingSkeleton'

export default function EscalationsPage() {
    const { data: escalationsData, isLoading: isLoadingEscalations, error: errorEscalations } = useEscalations()
    const { data: teamsData } = useEscalationTeams()
    const { data: registryData } = useRegistry()
    const [selectedTeam, setSelectedTeam] = useState<string>('')
    const [statusFilter, setStatusFilter] = useState<'all' | 'ongoing' | 'resolved'>('all')
    const [impactFilter, setImpactFilter] = useState<string>('all')
    const [tagFilter, setTagFilter] = useState<string>('')
    type DateFilter = '' | 'lastWeek' | 'last2Weeks' | 'lastMonth' | 'custom'
    const [dateFilter, setDateFilter] = useState<DateFilter>('lastWeek')
    const [customDateRange, setCustomDateRange] = useState<{ start?: string; end?: string }>({})
    type SortColumn = 'ticketId' | 'openedAt' | 'resolvedAt' | 'duration'
    const [sortColumn, setSortColumn] = useState<SortColumn>('openedAt')
    const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('desc')
    const [pageIndex, setPageIndex] = useState<number>(0)
    const pageSize = 15

    const { hasFullAccess, effectiveTeams, selectedTeam: teamFilterSelectedTeam } = useTeamFilter()
    const { actualEscalationTeams } = useAuth()
    const now = useNow()

    // Check if viewing as an escalation team (when "Escalated to My Team" section is visible)
    const isViewingAsEscalationTeam = useMemo(() => {
        if (!teamFilterSelectedTeam || actualEscalationTeams.length === 0) return false
        return actualEscalationTeams.includes(teamFilterSelectedTeam)
    }, [teamFilterSelectedTeam, actualEscalationTeams])

    // --- Formatting helpers ---
    const formatDate = (isoString?: string) => isoString ? new Date(isoString).toLocaleString(undefined, { dateStyle: 'short', timeStyle: 'short' }) : '-'
    const formatDuration = (start?: string, end?: string) => {
        if (!start) return '-'
        const endTime = end ? new Date(end).getTime() : now
        const durationMs = endTime - new Date(start).getTime()
        const minutes = Math.floor(durationMs / 1000 / 60)
        const hours = Math.floor(minutes / 60)
        const mins = minutes % 60
        return hours > 0 ? `${hours}h ${mins}m` : `${mins}m`
    }
    const getDurationColor = (minutes: number) => minutes < 30 ? 'text-green-700' : minutes < 120 ? 'text-yellow-700' : 'text-indigo-700 font-semibold'

    // --- Date range for date filter ---
    const dateRange = useMemo(() => {
        if (!dateFilter) return { from: undefined, to: undefined }
        if (dateFilter === 'custom') {
            if (!customDateRange.start || !customDateRange.end) return { from: undefined, to: undefined }
            return { from: customDateRange.start, to: customDateRange.end }
        }
        const now = new Date()
        const to = now.toISOString().split('T')[0]
        const fromDate = new Date(now)
        if (dateFilter === 'lastWeek') fromDate.setDate(now.getDate() - 6)
        else if (dateFilter === 'last2Weeks') fromDate.setDate(now.getDate() - 13)
        else if (dateFilter === 'lastMonth') fromDate.setMonth(now.getMonth() - 1)
        return { from: fromDate.toISOString().split('T')[0], to }
    }, [dateFilter, customDateRange])

    // --- Filtered Escalations ---
    const filteredEscalations = useMemo(() => {
        if (!escalationsData?.content) return []
        let baseEscalations = escalationsData.content

        // Full access -> show all, no access and no teams -> show nothing
        if (!hasFullAccess) {
            if (effectiveTeams.length === 0) {
                return [] // No teams and no full access -> no escalations
            }
            // Filter by escalating team (ticket owner) - use case-insensitive matching for robustness
            baseEscalations = baseEscalations.filter(esc => {
                if (!esc.escalatingTeam) return false
                const escalatingTeamTrimmed = esc.escalatingTeam.trim().toLowerCase()
                return effectiveTeams.some(team => team.trim().toLowerCase() === escalatingTeamTrimmed)
            })
        }

        // Apply team filter - filter by escalation team (team the escalation was escalated TO)
        // Use case-insensitive matching for robustness
        baseEscalations = baseEscalations.filter(esc => {
            if (!selectedTeam) return true
            if (!esc.team?.name) return false
            return esc.team.name.trim().toLowerCase() === selectedTeam.trim().toLowerCase()
        })

        // Apply status filter
        if (statusFilter === 'ongoing') {
            baseEscalations = baseEscalations.filter(esc => !esc.resolvedAt)
        } else if (statusFilter === 'resolved') {
            baseEscalations = baseEscalations.filter(esc => esc.resolvedAt)
        }

        // Apply impact filter
        if (impactFilter !== 'all') {
            baseEscalations = baseEscalations.filter(esc => esc.impact === impactFilter)
        }

        // Apply tag filter
        if (tagFilter) {
            baseEscalations = baseEscalations.filter(esc => esc.tags?.includes(tagFilter))
        }

        // Apply date filter
        if (dateRange.from) {
            const from = new Date(dateRange.from + 'T00:00:00')
            baseEscalations = baseEscalations.filter(esc => esc.openedAt && new Date(esc.openedAt) >= from)
        }
        if (dateRange.to) {
            const to = new Date(dateRange.to + 'T23:59:59')
            baseEscalations = baseEscalations.filter(esc => esc.openedAt && new Date(esc.openedAt) <= to)
        }

        // When viewing as escalation team ("Escalated for My Team"), deduplicate by ticketId
        // to match the home dashboard "Tickets We Own - Escalated" count
        // Keep the most recent escalation per ticket
        if (isViewingAsEscalationTeam && !hasFullAccess) {
            const ticketMap = new Map<string, typeof baseEscalations[0]>()
            baseEscalations.forEach(esc => {
                const existing = ticketMap.get(esc.ticketId)
                if (!existing || (esc.openedAt && (!existing.openedAt || esc.openedAt > existing.openedAt))) {
                    ticketMap.set(esc.ticketId, esc)
                }
            })
            baseEscalations = Array.from(ticketMap.values())
        }

        return baseEscalations
    }, [escalationsData, selectedTeam, statusFilter, impactFilter, tagFilter, dateRange, hasFullAccess, effectiveTeams, isViewingAsEscalationTeam])

    // --- Sorting ---
    const handleSort = (column: SortColumn) => {
        if (sortColumn === column) {
            setSortDirection(d => d === 'asc' ? 'desc' : 'asc')
        } else {
            setSortColumn(column)
            setSortDirection(column === 'openedAt' || column === 'resolvedAt' ? 'desc' : 'asc')
        }
        setPageIndex(0)
    }

    const sortedEscalations = useMemo(() => {
        return [...filteredEscalations].sort((a, b) => {
            let cmp = 0
            if (sortColumn === 'ticketId') {
                cmp = (a.ticketId || '').localeCompare(b.ticketId || '')
            } else if (sortColumn === 'openedAt') {
                cmp = (a.openedAt || '').localeCompare(b.openedAt || '')
            } else if (sortColumn === 'resolvedAt') {
                // Unresolved (null) sorts last regardless of direction
                if (!a.resolvedAt && !b.resolvedAt) cmp = 0
                else if (!a.resolvedAt) cmp = 1
                else if (!b.resolvedAt) cmp = -1
                else cmp = a.resolvedAt.localeCompare(b.resolvedAt)
            } else if (sortColumn === 'duration') {
                const aDur = a.openedAt ? (a.resolvedAt ? new Date(a.resolvedAt).getTime() : now) - new Date(a.openedAt).getTime() : 0
                const bDur = b.openedAt ? (b.resolvedAt ? new Date(b.resolvedAt).getTime() : now) - new Date(b.openedAt).getTime() : 0
                cmp = aDur - bDur
            }
            return sortDirection === 'asc' ? cmp : -cmp
        })
    }, [filteredEscalations, sortColumn, sortDirection, now])

    // --- Pagination ---
    const paginatedEscalations = useMemo(() => {
        const start = pageIndex * pageSize
        return sortedEscalations.slice(start, start + pageSize)
    }, [sortedEscalations, pageIndex])

    const totalPages = Math.ceil(filteredEscalations.length / pageSize)

    // --- Top 2 tags ---
    const topTags = useMemo(() => {
        const freqMap: Record<string, number> = {}
        filteredEscalations.forEach(esc => esc.tags?.forEach(tag => freqMap[tag] = (freqMap[tag] || 0) + 1))
        return Object.entries(freqMap)
            .sort((a, b) => b[1] - a[1])
            .slice(0, 5)
            .map(([tag, count]) => ({ tag, count }))
    }, [filteredEscalations])

    return (
        <div className="p-6 space-y-6">
            <h1 className="text-2xl font-bold mb-4">
                {hasFullAccess 
                    ? 'Escalations Dashboard - All Teams' 
                    : effectiveTeams.length > 0
                        ? `Escalations Dashboard - ${effectiveTeams.join(', ')}`
                        : 'Escalations Dashboard'
                }
            </h1>

            {isLoadingEscalations && <LoadingSkeleton />}
            {errorEscalations && (
                <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-800">
                    <p className="font-semibold">Error loading escalations</p>
                    <p className="text-sm mt-1">Unable to load escalation data. Please try refreshing the page.</p>
                </div>
            )}

            {/* Escalated to My Team Table (Escalation Team Only) */}
            <EscalatedToMyTeamTable />

            {/* Divider */}
            <div className="border-t-2 border-gray-300 my-8"></div>

            {/* All Escalations / Escalated for My Team Section */}
            <div className="space-y-6 bg-purple-50 p-6 rounded-lg border-2 border-purple-200">
                <div className="flex items-center justify-between">
                    <h2 className="text-xl font-bold text-purple-900">
                        {isViewingAsEscalationTeam ? 'Escalated for My Team' : 'All Escalations'}
                    </h2>
                    <span className="text-sm text-gray-600">{filteredEscalations.length} total</span>
                </div>

                {/* Filters */}
                <div className="flex gap-4 flex-wrap">
                <div className="flex items-center gap-2">
                    <label className="text-sm font-medium text-gray-700">Date:</label>
                    <select
                        data-testid="escalations-date-filter"
                        aria-label="Escalation date filter"
                        value={dateFilter}
                        onChange={e => { setDateFilter(e.target.value as DateFilter); setPageIndex(0) }}
                        className="p-2 border rounded"
                    >
                        <option value="">Any Date</option>
                        <option value="lastWeek">Last Week</option>
                        <option value="last2Weeks">Last 2 Weeks</option>
                        <option value="lastMonth">Last Month</option>
                        <option value="custom">Custom Range</option>
                    </select>
                    {dateFilter === 'custom' && (
                        <>
                            <input
                                type="date"
                                aria-label="Date filter start"
                                value={customDateRange.start || ''}
                                onChange={e => { setCustomDateRange(r => ({ ...r, start: e.target.value })); setPageIndex(0) }}
                                className="p-2 border rounded text-sm"
                            />
                            <span className="text-gray-500 text-sm">to</span>
                            <input
                                type="date"
                                aria-label="Date filter end"
                                value={customDateRange.end || ''}
                                onChange={e => { setCustomDateRange(r => ({ ...r, end: e.target.value })); setPageIndex(0) }}
                                className="p-2 border rounded text-sm"
                            />
                        </>
                    )}
                </div>
                <div>
                    <label className="text-sm font-medium text-gray-700 mr-2">Status:</label>
                    <select
                        data-testid="escalations-status-filter"
                        aria-label="Escalation status filter"
                        value={statusFilter}
                        onChange={e => { setStatusFilter(e.target.value as 'all' | 'ongoing' | 'resolved'); setPageIndex(0) }}
                        className="p-2 border rounded"
                    >
                        <option value="all">All</option>
                        <option value="ongoing">Ongoing</option>
                        <option value="resolved">Resolved</option>
                    </select>
                </div>
                <div>
                    <label className="text-sm font-medium text-gray-700 mr-2">Impact:</label>
                    <select
                        data-testid="escalations-impact-filter"
                        aria-label="Escalation impact filter"
                        value={impactFilter}
                        onChange={e => { setImpactFilter(e.target.value); setPageIndex(0) }}
                        className="p-2 border rounded"
                    >
                        <option value="all">All</option>
                        {registryData?.impacts?.map((impact: { code: string; label: string }) => (
                            <option key={impact.code} value={impact.code}>{impact.label}</option>
                        ))}
                    </select>
                </div>
                {hasFullAccess && (
                    <div>
                        <label className="text-sm font-medium text-gray-700 mr-2">Team:</label>
                        <select
                            data-testid="escalations-team-filter"
                            aria-label="Escalation team filter"
                            value={selectedTeam}
                            onChange={e => { setSelectedTeam(e.target.value); setPageIndex(0) }}
                            className="p-2 border rounded"
                        >
                            <option value="">All Teams</option>
                            {teamsData?.map(team => <option key={team.name} value={team.name}>{team.name}</option>)}
                        </select>
                    </div>
                )}
                <div>
                    <label className="text-sm font-medium text-gray-700 mr-2">Tag:</label>
                    <select
                        data-testid="escalations-tag-filter"
                        aria-label="Escalation tag filter"
                        value={tagFilter}
                        onChange={e => { setTagFilter(e.target.value); setPageIndex(0) }}
                        className="p-2 border rounded"
                    >
                        <option value="">All Tags</option>
                        {registryData?.tags?.map((tag: { code: string; label: string }) => (
                            <option key={tag.code} value={tag.code}>{tag.label}</option>
                        ))}
                    </select>
                </div>
            </div>

            {/* Top 5 Tags */}
            <div className="mb-4">
                <h3 className="text-sm font-semibold text-gray-600 uppercase tracking-wide mb-2">
                    Top 5 Tags {selectedTeam && hasFullAccess && `— ${selectedTeam}`}
                </h3>
                {topTags.length === 0 ? <p className="text-sm text-gray-500">No tags found.</p> : (
                    <div className="flex flex-col gap-1.5">
                        {topTags.map(({ tag, count }, idx) => {
                            const maxCount = topTags[0].count
                            const widthPercent = Math.min(100, (count / maxCount) * 100)
                            const palette = ['from-indigo-500 to-indigo-400', 'from-blue-400 to-cyan-300', 'from-violet-500 to-purple-400', 'from-sky-500 to-blue-400', 'from-teal-500 to-emerald-400']
                            const bgColor = palette[idx % palette.length]
                            return (
                                <div key={tag} className="flex items-center gap-3">
                                    <span className="shrink-0 bg-indigo-100 text-indigo-800 text-xs font-semibold px-2 py-0.5 rounded">{tag}</span>
                                    <div className="flex-1 h-4 rounded-full overflow-hidden bg-gray-200">
                                        <div className={`h-4 rounded-full bg-gradient-to-r ${bgColor} flex items-center justify-end pr-2 text-white font-semibold text-xs transition-all duration-500`} style={{ width: `${widthPercent}%` }}>
                                            {count}
                                        </div>
                                    </div>
                                </div>
                            )
                        })}
                    </div>
                )}
            </div>

            {filteredEscalations.length === 0 && !isLoadingEscalations && <p>No escalations found.</p>}

                {/* Escalations table */}
                <div className="overflow-x-auto border rounded-lg shadow-sm">
                    <table className="min-w-full divide-y">
                        <thead className="bg-gray-100">
                        <tr>
                            {(() => {
                                const SortableHeader = ({ col, label }: { col: SortColumn; label: string }) => (
                                    <th
                                        className="px-4 py-2 text-left text-xs font-bold text-gray-700 uppercase cursor-pointer select-none hover:bg-gray-200 transition-colors"
                                        onClick={() => handleSort(col)}
                                    >
                                        <span className="flex items-center gap-1">
                                            {label}
                                            <span className="text-gray-400">
                                                {sortColumn === col ? (sortDirection === 'asc' ? '↑' : '↓') : '↕'}
                                            </span>
                                        </span>
                                    </th>
                                )
                                return (
                                    <>
                                        <SortableHeader col="ticketId" label="Ticket ID" />
                                        {hasFullAccess && <th className="px-4 py-2 text-left text-xs font-bold text-gray-700 uppercase">Escalating Team</th>}
                                        {hasFullAccess && <th className="px-4 py-2 text-left text-xs font-bold text-gray-700 uppercase">Escalated To</th>}
                                        <th className="px-4 py-2 text-left text-xs font-bold text-gray-700 uppercase">Status</th>
                                        <th className="px-4 py-2 text-left text-xs font-bold text-gray-700 uppercase">Impact</th>
                                        <SortableHeader col="openedAt" label="Opened" />
                                        <SortableHeader col="resolvedAt" label="Resolved" />
                                        <SortableHeader col="duration" label="Duration" />
                                        <th className="px-4 py-2 text-left text-xs font-bold text-gray-700 uppercase">Tags</th>
                                        <th className="px-4 py-2 text-left text-xs font-bold text-gray-700 uppercase">Thread</th>
                                    </>
                                )
                            })()}
                        </tr>
                        </thead>
                    <tbody className="divide-y">
                    {paginatedEscalations.map(esc => {
                        const durationMs = esc.openedAt ? (esc.resolvedAt ? new Date(esc.resolvedAt).getTime() : now) - new Date(esc.openedAt).getTime() : 0
                        const durationMinutes = Math.floor(durationMs / 1000 / 60)
                        return (
                            <tr key={esc.id} className="hover:bg-gray-50 transition-colors">
                                <td className="px-4 py-2 text-sm">{esc.ticketId}</td>
                                {hasFullAccess && <td className="px-4 py-2 text-sm">{esc.escalatingTeam || '-'}</td>}
                                {hasFullAccess && <td className="px-4 py-2 text-sm">{esc.team?.name || '-'}</td>}
                                <td className="px-4 py-2">
                                        <span className={`px-2 py-1 rounded-full text-xs font-semibold ${esc.resolvedAt ? 'bg-green-200 text-green-800' : 'bg-yellow-200 text-yellow-800'}`}>
                                            {esc.resolvedAt ? 'Resolved' : 'Unresolved'}
                                        </span>
                                </td>
                                <td className="px-4 py-2">
                                    <span className="text-sm font-medium">{esc.impact || '-'}</span>
                                </td>
                                <td className="px-4 py-2 text-sm">{formatDate(esc.openedAt)}</td>
                                <td className="px-4 py-2 text-sm">{esc.resolvedAt ? formatDate(esc.resolvedAt) : 'Ongoing'}</td>
                                <td className={`px-4 py-2 text-sm ${getDurationColor(durationMinutes)}`}>{formatDuration(esc.openedAt, esc.resolvedAt ?? undefined)}</td>
                                <td className="px-4 py-2 text-sm">
                                    {esc.tags?.length ? esc.tags.map((tag, i) => <span key={i} className="bg-indigo-100 text-indigo-800 text-xs font-semibold px-2 py-0.5 rounded mr-1">{tag}</span>) : '-'}
                                </td>
                                <td className="px-4 py-2 text-sm">
                                    {esc.hasThread ? (
                                        <a
                                            href={`/api/escalations/${esc.id}/permalink`}
                                            target="_blank"
                                            rel="noopener noreferrer"
                                            className="text-blue-600 hover:underline"
                                        >
                                            View
                                        </a>
                                    ) : '-'}
                                </td>
                            </tr>
                        )
                    })}
                    </tbody>
                </table>
            </div>

                {/* Pagination buttons */}
                {filteredEscalations.length > pageSize && (
                    <div className="flex justify-between mt-4">
                        <button
                            className="px-4 py-2 bg-gray-200 rounded disabled:opacity-50"
                            onClick={() => setPageIndex(prev => Math.max(prev - 1, 0))}
                            disabled={pageIndex === 0}
                        >
                            Previous
                        </button>
                        <span>Page {pageIndex + 1} of {totalPages}</span>
                        <button
                            className="px-4 py-2 bg-gray-200 rounded disabled:opacity-50"
                            onClick={() => setPageIndex(prev => Math.min(prev + 1, totalPages - 1))}
                            disabled={pageIndex >= totalPages - 1}
                        >
                            Next
                        </button>
                    </div>
                )}
            </div> {/* End of purple container */}
        </div>
    )
}
