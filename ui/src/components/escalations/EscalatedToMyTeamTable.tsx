'use client'

import { useState, useMemo } from 'react'
import { useEscalations, useRegistry } from '@/lib/hooks'
import { useAuth } from '@/contexts/AuthContext'
import { useTeamFilter } from '@/contexts/TeamFilterContext'

export default function EscalatedToMyTeamTable() {
    const { isEscalationTeam, actualEscalationTeams } = useAuth()
    const { selectedTeam } = useTeamFilter()
    const { data: escalationsData, isLoading, error } = useEscalations()
    const { data: registryData } = useRegistry()
    
    const [statusFilter, setStatusFilter] = useState<'all' | 'ongoing' | 'resolved'>('all')
    const [impactFilter, setImpactFilter] = useState<string>('all')
    const [pageIndex, setPageIndex] = useState(0)
    const pageSize = 15

    // Only show when viewing one of the user's actual escalation teams
    const isViewingEscalationsOnly = useMemo(() => {
        if (!selectedTeam || actualEscalationTeams.length === 0) return false
        return actualEscalationTeams.includes(selectedTeam)
    }, [selectedTeam, actualEscalationTeams])

    // Helper to map impact code to label
    const impactLabel = (code?: string) => {
        if (!code) return '-'
        const match = registryData?.impacts?.find((i: { code: string; label: string }) => i.code === code)
        return match?.label || code
    }

    // Filter escalations escalated TO the currently selected escalation team
    // "Escalated TO my team" means escalations that were escalated TO your team (not tickets you own)
    const filteredEscalations = useMemo(() => {
        if (!escalationsData?.content || !selectedTeam) return []
        
        // Filter escalations where the target team (escalated TO) matches the selected escalation team
        // Use case-insensitive comparison to handle format differences
        let filtered = escalationsData.content.filter(esc => {
            if (!esc.team?.name) return false
            return esc.team.name.trim().toLowerCase() === selectedTeam.trim().toLowerCase()
        })

        // Apply status filter
        if (statusFilter === 'ongoing') {
            filtered = filtered.filter(esc => !esc.resolvedAt)
        } else if (statusFilter === 'resolved') {
            filtered = filtered.filter(esc => esc.resolvedAt)
        }

        // Apply impact filter
        if (impactFilter !== 'all') {
            filtered = filtered.filter(esc => esc.impact === impactFilter)
        }
        
        return filtered
    }, [escalationsData, selectedTeam, statusFilter, impactFilter])

    // Pagination
    const paginatedEscalations = useMemo(() => {
        const start = pageIndex * pageSize
        return filteredEscalations.slice(start, start + pageSize)
    }, [filteredEscalations, pageIndex])

    const totalPages = Math.ceil(filteredEscalations.length / pageSize)

    // Top 2 tags
    const topTags = useMemo(() => {
        const freqMap: Record<string, number> = {}
        filteredEscalations.forEach(esc => esc.tags?.forEach(tag => freqMap[tag] = (freqMap[tag] || 0) + 1))
        return Object.entries(freqMap)
            .sort((a, b) => b[1] - a[1])
            .slice(0, 2)
            .map(([tag, count]) => ({ tag, count }))
    }, [filteredEscalations])

    // Formatting helpers
    const formatDate = (isoString?: string) => isoString ? new Date(isoString).toLocaleString(undefined, { dateStyle: 'short', timeStyle: 'short' }) : '-'
    const formatDuration = (start?: string, end?: string) => {
        if (!start) return '-'
        const endTime = end ? new Date(end).getTime() : Date.now()
        const durationMs = endTime - new Date(start).getTime()
        const minutes = Math.floor(durationMs / 1000 / 60)
        const hours = Math.floor(minutes / 60)
        const mins = minutes % 60
        return hours > 0 ? `${hours}h ${mins}m` : `${mins}m`
    }
    const getDurationColor = (minutes: number) => minutes < 30 ? 'text-green-700' : minutes < 120 ? 'text-yellow-700' : 'text-indigo-700 font-semibold'

    // Don't show if not in escalation team OR not viewing escalations team specifically (after all hooks)
    if (!isEscalationTeam || !isViewingEscalationsOnly) {
        return null
    }

    if (isLoading) return <p>Loading...</p>
    if (error) return <p className="text-red-500">Error loading escalations</p>

    return (
        <div className="space-y-6 bg-purple-50 p-6 rounded-lg border-2 border-purple-200">
            <div className="flex items-center justify-between">
                <h2 className="text-xl font-bold text-purple-900">Escalated to My Team</h2>
                <span className="text-sm text-gray-600">{filteredEscalations.length} total</span>
            </div>

            {/* Filters */}
            <div className="flex gap-4 flex-wrap">
                <div>
                    <label className="text-sm font-medium text-gray-700 mr-2">Status:</label>
                    <select 
                        value={statusFilter} 
                        onChange={e => { setStatusFilter(e.target.value as 'all' | 'ongoing' | 'resolved'); setPageIndex(0) }}
                        className="p-2 border rounded bg-white"
                    >
                        <option value="all">All</option>
                        <option value="ongoing">Ongoing</option>
                        <option value="resolved">Resolved</option>
                    </select>
                </div>
                <div>
                    <label className="text-sm font-medium text-gray-700 mr-2">Impact:</label>
                    <select 
                        value={impactFilter} 
                        onChange={e => { setImpactFilter(e.target.value); setPageIndex(0) }}
                        className="p-2 border rounded bg-white"
                    >
                        <option value="all">All</option>
                        {registryData?.impacts?.map((impact: { code: string; label: string }) => (
                            <option key={impact.code} value={impact.code}>{impact.label}</option>
                        ))}
                    </select>
                </div>
            </div>

            {/* Top 2 Tags */}
            {topTags.length > 0 && (
                <div>
                    <h3 className="text-lg font-semibold mb-3 text-gray-800">Top 2 Tags</h3>
                    <div className="flex flex-col gap-4">
                        {topTags.map(({ tag, count }, idx) => {
                            const maxCount = topTags[0].count
                            const widthPercent = Math.min(100, (count / maxCount) * 100)
                            const bgColor = idx === 0 ? 'from-purple-500 to-purple-400' : 'from-pink-400 to-pink-300'
                            return (
                                <div key={tag} className="flex items-center space-x-4">
                                    <span className="w-32 font-semibold text-gray-700">{tag}</span>
                                    <div className="flex-1 h-10 rounded-full overflow-hidden bg-gray-200 shadow-inner">
                                        <div className={`h-10 rounded-full bg-gradient-to-r ${bgColor} flex items-center justify-end pr-4 text-white font-bold text-sm transition-all duration-700`} style={{ width: `${widthPercent}%` }}>
                                            {count}
                                        </div>
                                    </div>
                                </div>
                            )
                        })}
                    </div>
                </div>
            )}

            {/* Table */}
            {filteredEscalations.length === 0 ? (
                <p className="text-gray-500 text-center py-8">No escalations found for your team.</p>
            ) : (
                <>
                    <div className="overflow-x-auto border rounded-lg shadow-sm bg-white">
                        <table className="min-w-full divide-y">
                            <thead className="bg-purple-100">
                                <tr>
                                    <th className="px-4 py-2 text-left text-xs font-bold text-purple-900 uppercase">Ticket ID</th>
                                    <th className="px-4 py-2 text-left text-xs font-bold text-purple-900 uppercase">Status</th>
                                    <th className="px-4 py-2 text-left text-xs font-bold text-purple-900 uppercase">Impact</th>
                                    <th className="px-4 py-2 text-left text-xs font-bold text-purple-900 uppercase">Opened</th>
                                    <th className="px-4 py-2 text-left text-xs font-bold text-purple-900 uppercase">Resolved</th>
                                    <th className="px-4 py-2 text-left text-xs font-bold text-purple-900 uppercase">Duration</th>
                                    <th className="px-4 py-2 text-left text-xs font-bold text-purple-900 uppercase">Tags</th>
                                    <th className="px-4 py-2 text-left text-xs font-bold text-purple-900 uppercase">Thread</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y">
                                {paginatedEscalations.map(esc => {
                                    const durationMs = esc.openedAt ? (esc.resolvedAt ? new Date(esc.resolvedAt).getTime() : Date.now()) - new Date(esc.openedAt).getTime() : 0
                                    const durationMinutes = Math.floor(durationMs / 1000 / 60)
                                    return (
                                        <tr key={esc.id} className="hover:bg-purple-50 transition-colors">
                                            <td className="px-4 py-2 text-sm font-medium">{esc.ticketId}</td>
                                            <td className="px-4 py-2">
                                                <span className={`px-2 py-1 rounded-full text-xs font-semibold ${esc.resolvedAt ? 'bg-green-200 text-green-800' : 'bg-yellow-200 text-yellow-800'}`}>
                                                    {esc.resolvedAt ? 'Resolved' : 'Ongoing'}
                                                </span>
                                            </td>
                                            <td className="px-4 py-2">
                                                <span className="text-sm font-medium">{impactLabel(esc.impact || undefined)}</span>
                                            </td>
                                            <td className="px-4 py-2 text-sm">{formatDate(esc.openedAt)}</td>
                                            <td className="px-4 py-2 text-sm">{esc.resolvedAt ? formatDate(esc.resolvedAt) : 'Ongoing'}</td>
                                            <td className={`px-4 py-2 text-sm ${getDurationColor(durationMinutes)}`}>{formatDuration(esc.openedAt, esc.resolvedAt ?? undefined)}</td>
                                            <td className="px-4 py-2 text-sm">
                                                {esc.tags?.length ? esc.tags.map((tag, i) => <span key={i} className="bg-purple-100 text-purple-800 text-xs font-semibold px-2 py-0.5 rounded mr-1">{tag}</span>) : '-'}
                                            </td>
                                            <td className="px-4 py-2 text-sm">{esc.threadLink ? <a href={esc.threadLink} target="_blank" rel="noopener noreferrer" className="text-blue-600 hover:underline">View</a> : '-'}</td>
                                        </tr>
                                    )
                                })}
                            </tbody>
                        </table>
                    </div>

                    {/* Pagination */}
                    {filteredEscalations.length > pageSize && (
                        <div className="flex justify-between items-center">
                            <button
                                className="px-4 py-2 bg-purple-600 text-white rounded disabled:opacity-50 disabled:cursor-not-allowed hover:bg-purple-700"
                                onClick={() => setPageIndex(prev => Math.max(0, prev - 1))}
                                disabled={pageIndex === 0}
                            >
                                Previous
                            </button>
                            <span className="text-sm text-gray-600">
                                Page {pageIndex + 1} of {totalPages}
                            </span>
                            <button
                                className="px-4 py-2 bg-purple-600 text-white rounded disabled:opacity-50 disabled:cursor-not-allowed hover:bg-purple-700"
                                onClick={() => setPageIndex(prev => Math.min(totalPages - 1, prev + 1))}
                                disabled={pageIndex >= totalPages - 1}
                            >
                                Next
                            </button>
                        </div>
                    )}
                </>
            )}
        </div>
    )
}

