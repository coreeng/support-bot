'use client'

import { useState, useMemo } from 'react'
import { getDateRangeFromFilter, PRESET_DAYS } from '@/lib/dateRange'
import { useEscalations, useRegistry } from '@/lib/hooks'
import { useAuth } from '@/hooks/useAuth'
import { useTeamFilter } from '@/contexts/TeamFilterContext'
import { useNow } from '@/hooks/useNow'
import { ArrowDown, ArrowUp, ArrowUpDown } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { SingleSelectFilter } from '@/components/ui/single-select-filter'

export default function EscalatedToMyTeamTable() {
    const { isEscalationTeam, actualEscalationTeams } = useAuth()
    const { selectedTeam } = useTeamFilter()
    const { data: escalationsData, isLoading, error } = useEscalations()
    const { data: registryData } = useRegistry()
    const now = useNow()

    const [statusFilter, setStatusFilter] = useState<'all' | 'ongoing' | 'resolved'>('all')
    const [impactFilter, setImpactFilter] = useState<string>('all')
    const [tagFilter, setTagFilter] = useState<string>('')
    type DateFilter = '' | 'lastWeek' | 'last2Weeks' | 'lastMonth' | 'custom'
    const [dateFilter, setDateFilter] = useState<DateFilter>('lastWeek')
    const [customDateRange, setCustomDateRange] = useState<{ start?: string; end?: string }>({})
    type SortColumn = 'ticketId' | 'openedAt' | 'resolvedAt' | 'duration'
    const [sortColumn, setSortColumn] = useState<SortColumn>('openedAt')
    const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('desc')
    const [pageIndex, setPageIndex] = useState(0)
    const pageSize = 15

    // Empty string (= "Any Date") is not in presetDays so falls through to
    // { from: undefined, to: undefined } inside getDateRangeFromFilter, which is correct.
    const dateRange = useMemo(
        () =>
            getDateRangeFromFilter({
                dateFilter,
                customDateRange,
                customValue: 'custom',
                fallbackValue: 'lastWeek',
                presetDays: {
                    lastWeek: PRESET_DAYS.lastWeek,
                    last2Weeks: PRESET_DAYS.last2Weeks,
                    lastMonth: PRESET_DAYS.lastMonth,
                },
            }),
        [dateFilter, customDateRange]
    )

    const handleSort = (column: SortColumn) => {
        if (sortColumn === column) {
            setSortDirection(d => d === 'asc' ? 'desc' : 'asc')
        } else {
            setSortColumn(column)
            setSortDirection(column === 'openedAt' || column === 'resolvedAt' ? 'desc' : 'asc')
        }
        setPageIndex(0)
    }

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

        // Apply tag filter
        if (tagFilter) {
            filtered = filtered.filter(esc => esc.tags?.includes(tagFilter))
        }

        // Apply date filter
        if (dateRange.from) {
            const from = new Date(dateRange.from + 'T00:00:00')
            filtered = filtered.filter(esc => esc.openedAt && new Date(esc.openedAt) >= from)
        }
        if (dateRange.to) {
            const to = new Date(dateRange.to + 'T23:59:59')
            filtered = filtered.filter(esc => esc.openedAt && new Date(esc.openedAt) <= to)
        }

        return filtered
    }, [escalationsData, selectedTeam, statusFilter, impactFilter, tagFilter, dateRange])

    // Sorting
    const sortedEscalations = useMemo(() => {
        return [...filteredEscalations].sort((a, b) => {
            let cmp = 0
            if (sortColumn === 'ticketId') {
                cmp = (a.ticketId || '').localeCompare(b.ticketId || '')
            } else if (sortColumn === 'openedAt') {
                cmp = (a.openedAt || '').localeCompare(b.openedAt || '')
            } else if (sortColumn === 'resolvedAt') {
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

    // Pagination
    const paginatedEscalations = useMemo(() => {
        const start = pageIndex * pageSize
        return sortedEscalations.slice(start, start + pageSize)
    }, [sortedEscalations, pageIndex])

    const totalPages = Math.ceil(sortedEscalations.length / pageSize)

    // Top 5 tags
    const topTags = useMemo(() => {
        const freqMap: Record<string, number> = {}
        filteredEscalations.forEach(esc => esc.tags?.forEach(tag => freqMap[tag] = (freqMap[tag] || 0) + 1))
        return Object.entries(freqMap)
            .sort((a, b) => b[1] - a[1])
            .slice(0, 5)
            .map(([tag, count]) => ({ tag, count }))
    }, [filteredEscalations])

    // Formatting helpers
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
    const getDurationColor = (minutes: number) => minutes < 30 ? 'text-success' : minutes < 120 ? 'text-warning' : 'text-destructive font-semibold'

    // Don't show if not in escalation team OR not viewing escalations team specifically (after all hooks)
    if (!isEscalationTeam || !isViewingEscalationsOnly) {
        return null
    }

    if (isLoading) return <p>Loading...</p>
    if (error) return <p className="text-destructive">Error loading escalations</p>

    const ANY_DATE = '__any'
    const fromAny = (v: string) => (v === ANY_DATE ? '' : v)
    const toAny = (v: string) => (v === '' ? ANY_DATE : v)

    return (
        <div className="space-y-4 rounded-xl border bg-card p-6">
            <div className="flex items-start justify-between gap-4">
                <div>
                    <h2 className="text-base font-semibold text-foreground">Escalated to My Team</h2>
                    <p className="mt-1 text-xs text-muted-foreground">Escalations routed to your team for handling</p>
                </div>
                <div className="flex items-center gap-2">
                    <Select
                        value={toAny(dateFilter)}
                        onValueChange={(v) => { setDateFilter(fromAny(v) as DateFilter); setPageIndex(0) }}
                    >
                        <SelectTrigger className="w-[160px]"><SelectValue /></SelectTrigger>
                        <SelectContent>
                            <SelectItem value={ANY_DATE}>Any Date</SelectItem>
                            <SelectItem value="lastWeek">Last Week</SelectItem>
                            <SelectItem value="last2Weeks">Last 2 Weeks</SelectItem>
                            <SelectItem value="lastMonth">Last Month</SelectItem>
                            <SelectItem value="custom">Custom Range</SelectItem>
                        </SelectContent>
                    </Select>
                    {dateFilter === 'custom' && (
                        <>
                            <Input type="date" aria-label="Date filter start"
                                   value={customDateRange.start || ''}
                                   onChange={e => { setCustomDateRange(r => ({ ...r, start: e.target.value })); setPageIndex(0) }}
                                   className="w-[150px]"/>
                            <Input type="date" aria-label="Date filter end"
                                   value={customDateRange.end || ''}
                                   onChange={e => { setCustomDateRange(r => ({ ...r, end: e.target.value })); setPageIndex(0) }}
                                   className="w-[150px]"/>
                        </>
                    )}
                </div>
            </div>

            <div className="flex flex-wrap items-center gap-2">
                <SingleSelectFilter
                    title="Status"
                    value={statusFilter !== 'all' ? statusFilter : undefined}
                    onChange={(v) => { setStatusFilter((v ?? 'all') as 'all' | 'ongoing' | 'resolved'); setPageIndex(0) }}
                    showSearch={false}
                    options={[
                        { label: 'Ongoing', value: 'ongoing' },
                        { label: 'Resolved', value: 'resolved' },
                    ]}
                />
                <SingleSelectFilter
                    title="Impact"
                    value={impactFilter !== 'all' ? impactFilter : undefined}
                    onChange={(v) => { setImpactFilter(v ?? 'all'); setPageIndex(0) }}
                    options={(registryData?.impacts ?? []).map((impact: { code: string; label: string }) => ({
                        label: impact.label, value: impact.code,
                    }))}
                />
                <SingleSelectFilter
                    title="Tag"
                    value={tagFilter || undefined}
                    onChange={(v) => { setTagFilter(v ?? ''); setPageIndex(0) }}
                    options={(registryData?.tags ?? []).map((tag: { code: string; label: string }) => ({
                        label: tag.label, value: tag.code,
                    }))}
                />
            </div>

            {/* Top 5 Tags */}
            {topTags.length > 0 && (
                <div>
                    <h3 className="text-xs font-medium text-muted-foreground uppercase tracking-wide mb-2">Top 5 Tags</h3>
                    <div className="flex flex-col gap-1.5">
                        {topTags.map(({ tag, count }) => {
                            const maxCount = topTags[0].count
                            const widthPercent = Math.min(100, (count / maxCount) * 100)
                            return (
                                <div key={tag} className="flex items-center gap-3">
                                    <Badge variant="outline" className="shrink-0">{tag}</Badge>
                                    <div className="flex-1 h-4 rounded-full overflow-hidden bg-muted">
                                        <div className="h-4 rounded-full bg-primary flex items-center justify-end pr-2 text-primary-foreground font-mono font-semibold text-xs tabular-nums transition-all duration-500" style={{ width: `${widthPercent}%` }}>
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
                <p className="text-muted-foreground text-center py-8">No escalations found for your team.</p>
            ) : (
                <>
                    <div className="overflow-x-auto border rounded-lg bg-card">
                        <table className="min-w-full divide-y">
                            <thead className="bg-muted">
                                <tr>
                                    {(() => {
                                        const SortableHeader = ({ col, label }: { col: SortColumn; label: string }) => (
                                            <th
                                                className="px-4 py-2 text-left text-xs font-bold text-foreground uppercase cursor-pointer select-none hover:bg-muted/70 transition-colors"
                                                onClick={() => handleSort(col)}
                                            >
                                                <span className="inline-flex items-center gap-1">
                                                    {label}
                                                    {sortColumn === col
                                                        ? (sortDirection === 'asc' ? <ArrowUp className="h-3.5 w-3.5" /> : <ArrowDown className="h-3.5 w-3.5" />)
                                                        : <ArrowUpDown className="h-3.5 w-3.5 text-muted-foreground" />}
                                                </span>
                                            </th>
                                        )
                                        return (
                                            <>
                                                <SortableHeader col="ticketId" label="Ticket ID" />
                                                <th className="px-4 py-2 text-left text-xs font-bold text-foreground uppercase">Status</th>
                                                <th className="px-4 py-2 text-left text-xs font-bold text-foreground uppercase">Impact</th>
                                                <SortableHeader col="openedAt" label="Opened" />
                                                <SortableHeader col="resolvedAt" label="Resolved" />
                                                <SortableHeader col="duration" label="Duration" />
                                                <th className="px-4 py-2 text-left text-xs font-bold text-foreground uppercase">Tags</th>
                                                <th className="px-4 py-2 text-left text-xs font-bold text-foreground uppercase">Thread</th>
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
                                        <tr key={esc.id} className="hover:bg-accent transition-colors">
                                            <td className="px-4 py-2 text-sm font-medium">{esc.ticketId}</td>
                                            <td className="px-4 py-2">
                                                <span className={`px-2 py-1 rounded-full text-xs font-semibold ${esc.resolvedAt ? 'bg-success/20 text-success' : 'bg-warning/20 text-warning'}`}>
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
                                                {esc.tags?.length ? esc.tags.map((tag, i) => <Badge key={i} variant="outline" className="mr-1">{tag}</Badge>) : '-'}
                                            </td>
                                            <td className="px-4 py-2 text-sm">
                                                {esc.hasThread ? (
                                                    <a
                                                        href={`/api/escalations/${esc.id}/permalink`}
                                                        target="_blank"
                                                        rel="noopener noreferrer"
                                                        className="text-link hover:underline"
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

                    {/* Pagination */}
                    {filteredEscalations.length > pageSize && (
                        <div className="flex items-center justify-between gap-4">
                            <Button
                                variant="outline"
                                onClick={() => setPageIndex(prev => Math.max(0, prev - 1))}
                                disabled={pageIndex === 0}
                            >
                                Previous
                            </Button>
                            <span className="text-sm text-muted-foreground">
                                Page {pageIndex + 1} of {totalPages}
                            </span>
                            <Button
                                variant="outline"
                                onClick={() => setPageIndex(prev => Math.min(totalPages - 1, prev + 1))}
                                disabled={pageIndex >= totalPages - 1}
                            >
                                Next
                            </Button>
                        </div>
                    )}
                </>
            )}
        </div>
    )
}

