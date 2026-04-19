'use client'

import React, { useEffect, useMemo, useState } from 'react'
import { ChevronUp, ChevronDown, ChevronLeft, ChevronRight, Search, Info, BarChart3, Eye } from 'lucide-react'
import * as Tooltip from '@radix-ui/react-tooltip'
import { useTenantInsightsStats, useEscalationBreakdown } from '@/lib/hooks'
import { useUrlParams, enumValidator, isoDateValidator } from '@/lib/hooks/useUrlParams'
import { getDateRangeFromFilter, PRESET_DAYS } from '@/lib/dateRange'
import { formatDuration } from '@/lib/utils/format'
import type { RepoInsights } from '@/lib/types/dashboard'
import InFlightPrsTab from './in-flight-prs'

type StatsSortKey = 'severity' | 'repo' | 'team' | 'prCount' | 'openCount' | 'escalatedCount' | 'breachedCount' | 'p50' | 'p90' | 'p99'
type SortDir = 'asc' | 'desc'

function durationStyle(seconds: number): string {
    if (seconds < 14400) return 'text-emerald-700 bg-emerald-50 ring-emerald-200'
    if (seconds < 86400) return 'text-amber-700 bg-amber-50 ring-amber-200'
    return 'text-red-700 bg-red-50 ring-red-200'
}

function compareBySeverity(a: RepoInsights, b: RepoInsights): number {
    return (a.breachedCount - b.breachedCount)
        || (a.escalatedCount - b.escalatedCount)
        || (a.prCount - b.prCount)
}

function compareByKey(a: RepoInsights, b: RepoInsights, key: StatsSortKey, dir: SortDir): number {
    let cmp = 0
    switch (key) {
        case 'severity': cmp = compareBySeverity(a, b); break
        case 'repo': cmp = a.repo.localeCompare(b.repo); break
        case 'team': cmp = a.owningTeam.localeCompare(b.owningTeam); break
        case 'prCount': cmp = a.prCount - b.prCount; break
        case 'openCount': cmp = a.openCount - b.openCount; break
        case 'escalatedCount': cmp = a.escalatedCount - b.escalatedCount; break
        case 'breachedCount': cmp = a.breachedCount - b.breachedCount; break
        case 'p50': cmp = a.p50Seconds - b.p50Seconds; break
        case 'p90': cmp = a.p90Seconds - b.p90Seconds; break
        case 'p99': cmp = a.p99Seconds - b.p99Seconds; break
        default: key satisfies never
    }
    return dir === 'desc' ? -cmp : cmp
}

type TabKey = 'stats' | 'inflight'

const tabs = [
    { key: 'stats' as const, label: 'PR Activity & SLA Health', icon: BarChart3, color: 'blue' },
    { key: 'inflight' as const, label: 'In-Flight PRs', icon: Eye, color: 'purple' },
]

export default function TenantRequestsPage() {
    const [params, setParams] = useUrlParams(
        { tab: 'stats', dateFilter: 'lastMonth', dateFrom: '', dateTo: '' },
        {
            tab: enumValidator(['stats', 'inflight'] as const, 'stats'),
            dateFilter: enumValidator(['lastWeek', 'last2Weeks', 'lastMonth', 'lastYear', 'custom'] as const, 'lastMonth'),
            dateFrom: isoDateValidator,
            dateTo: isoDateValidator,
        },
    )
    const activeTab = params.tab as TabKey
    const dateFilter = params.dateFilter as 'lastWeek' | 'last2Weeks' | 'lastMonth' | 'lastYear' | 'custom'

    const isDateRangeValid = !(dateFilter === 'custom' && params.dateFrom && params.dateTo && params.dateFrom > params.dateTo)

    useEffect(() => {
        if (dateFilter === 'custom' && params.dateFrom && params.dateTo && params.dateFrom > params.dateTo) {
            setParams({ dateFilter: 'lastMonth', dateFrom: '', dateTo: '' })
        }
    }, [dateFilter, params.dateFrom, params.dateTo, setParams])

    const dateRange = useMemo(
        () =>
            getDateRangeFromFilter({
                dateFilter,
                customDateRange: { start: params.dateFrom || undefined, end: params.dateTo || undefined },
                customValue: 'custom',
                fallbackValue: 'lastMonth',
                presetDays: {
                    lastWeek: PRESET_DAYS.lastWeek,
                    last2Weeks: PRESET_DAYS.last2Weeks,
                    lastMonth: PRESET_DAYS.lastMonth,
                    lastYear: PRESET_DAYS.lastYear,
                },
            }),
        [dateFilter, params.dateFrom, params.dateTo]
    )

    const [search, setSearch] = useState('')
    const [sortKey, setSortKey] = useState<StatsSortKey>('severity')
    const [sortDir, setSortDir] = useState<SortDir>('desc')
    const [page, setPage] = useState(0)
    const pageSize = 20

    const { data: realRepos, isLoading: statsLoading, error: statsError } = useTenantInsightsStats(
        isDateRangeValid ? dateRange.from : undefined,
        isDateRangeValid ? dateRange.to : undefined,
        isDateRangeValid && activeTab === 'stats'
    )

    const { data: breakdown, isLoading: breakdownLoading, error: breakdownError } = useEscalationBreakdown(
        isDateRangeValid ? dateRange.from : undefined,
        isDateRangeValid ? dateRange.to : undefined,
        isDateRangeValid && activeTab === 'stats'
    )

    const isLoading = statsLoading || breakdownLoading
    const error = statsError || breakdownError

    const repos = realRepos ?? []

    // Intervention rate = manual escalations only; bot escalations are automated
    // workflow (SLA breach) and don't represent human intervention.
    const interventionRate = breakdown && breakdown.totalPrTickets > 0
        ? Math.round((breakdown.manuallyEscalatedTickets / breakdown.totalPrTickets) * 100)
        : null

    const totals = useMemo(() => {
        if (repos.length === 0) {
            return { prCount: 0, openCount: 0, escalatedCount: 0, breachedCount: 0 }
        }
        return repos.reduce(
            (acc, r) => ({
                prCount: acc.prCount + r.prCount,
                openCount: acc.openCount + r.openCount,
                escalatedCount: acc.escalatedCount + r.escalatedCount,
                breachedCount: acc.breachedCount + r.breachedCount,
            }),
            { prCount: 0, openCount: 0, escalatedCount: 0, breachedCount: 0 }
        )
    }, [repos])

    const filteredAndSorted = useMemo(() => {
        const q = search.toLowerCase()
        const filtered = q
            ? repos.filter(r => r.repo.toLowerCase().includes(q) || r.owningTeam.toLowerCase().includes(q))
            : repos
        return [...filtered].sort((a, b) => compareByKey(a, b, sortKey, sortDir))
    }, [repos, search, sortKey, sortDir])

    const totalPages = Math.ceil(filteredAndSorted.length / pageSize)
    const pagedRepos = filteredAndSorted.slice(page * pageSize, (page + 1) * pageSize)

    const handleSort = (key: StatsSortKey) => {
        setPage(0)
        if (sortKey === key) {
            setSortDir(d => d === 'desc' ? 'asc' : 'desc')
        } else {
            setSortKey(key)
            setSortDir(key === 'repo' || key === 'team' ? 'asc' : 'desc')
        }
    }

    const handleSearch = (value: string) => {
        setSearch(value)
        setPage(0)
    }

    const repoCount = repos.length
    const noSlaRepoCount = repos.filter(r => r.hasSla === false).length
    useEffect(() => {
        const missingHasSla = repos.filter(r => r.hasSla === undefined)
        if (missingHasSla.length > 0) {
            console.warn(
                `[tenant-requests] ${missingHasSla.length} repo(s) missing hasSla — likely API/UI version skew: ${missingHasSla.map(r => r.repo).join(', ')}`
            )
        }
    }, [repos])

    return (
        <Tooltip.Provider delayDuration={200}>
        <div className="min-h-screen bg-gradient-to-b from-slate-50 to-slate-100">
            <div className="sticky top-0 z-10 bg-white shadow-md border-b border-gray-200">
                <div className="max-w-[1600px] mx-auto px-8 py-4">
                    <div className="flex items-center justify-between mb-3">
                        <div>
                            <h1 className="text-2xl font-bold text-gray-900">Tenant Requests</h1>
                            <p className="text-xs text-gray-500 mt-0.5">PR tracking, review lifecycle, and SLA health</p>
                        </div>
                    </div>

                    {activeTab === 'stats' && (
                        <div className="flex flex-wrap items-center gap-2 py-2">
                            <select
                                value={dateFilter}
                                onChange={e => {
                                    const next = e.target.value
                                    setParams(next !== 'custom'
                                        ? { dateFilter: next, dateFrom: '', dateTo: '' }
                                        : { dateFilter: next })
                                    setPage(0)
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
                                        onChange={e => {
                                            setParams({ dateFrom: e.target.value })
                                            setPage(0)
                                        }}
                                        className="border rounded px-2 py-1 text-sm"
                                    />
                                    <input
                                        type="date"
                                        value={params.dateTo}
                                        onChange={e => {
                                            setParams({ dateTo: e.target.value })
                                            setPage(0)
                                        }}
                                        className="border rounded px-2 py-1 text-sm"
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
                    )}
                </div>
            </div>

            <div className="max-w-[1600px] mx-auto px-8 py-6">
                {/* Tab Navigation */}
                <div className="bg-white rounded-xl shadow-lg border border-gray-200 overflow-hidden">
                    <div className="flex border-b border-gray-200 bg-gradient-to-r from-gray-50 to-gray-100">
                        {tabs.map(tab => {
                            const isActive = activeTab === tab.key
                            const colorClasses: Record<string, string> = {
                                blue: isActive ? 'border-blue-600 bg-blue-50' : 'border-transparent hover:bg-blue-50',
                                purple: isActive ? 'border-purple-600 bg-purple-50' : 'border-transparent hover:bg-purple-50',
                            }
                            const activeTextColors: Record<string, string> = {
                                blue: 'text-blue-700',
                                purple: 'text-purple-700',
                            }
                            const textColor = isActive ? activeTextColors[tab.color] : 'text-gray-600'
                            const Icon = tab.icon
                            return (
                                <button
                                    key={tab.key}
                                    onClick={() => setParams({ tab: tab.key })}
                                    className={`flex-1 flex items-center justify-center gap-2 px-6 py-4 text-sm font-semibold border-b-3 transition-all duration-200 ${colorClasses[tab.color]}`}
                                >
                                    <Icon className={`w-5 h-5 ${isActive ? 'animate-pulse' : ''}`} />
                                    <span className={textColor}>{tab.label}</span>
                                </button>
                            )
                        })}
                    </div>

                    <div className="p-8 min-h-[600px]">
                        {activeTab === 'stats' && (
                            <div className="space-y-6">
                                <div>
                                    <h2 className="text-sm font-semibold text-slate-900">PR Activity & SLA Health</h2>
                                    <p className="text-xs text-slate-400 mt-0.5">Pull request tracking across repositories</p>
                                </div>

                                <div className="grid grid-cols-2 lg:grid-cols-7 gap-4">
                                    <StatCard
                                        label="Repositories"
                                        value={repoCount}
                                        isLoading={isLoading}
                                        gradient="from-slate-600 to-slate-700"
                                        iconBg="bg-slate-500/30"
                                    />
                                    <StatCard
                                        label="No SLA Repos"
                                        value={noSlaRepoCount}
                                        isLoading={isLoading}
                                        gradient="from-amber-500 to-amber-600"
                                        iconBg="bg-amber-400/30"
                                    />
                                    <StatCard
                                        label="Total PRs"
                                        value={totals.prCount}
                                        isLoading={isLoading}
                                        gradient="from-indigo-500 to-indigo-600"
                                        iconBg="bg-indigo-400/30"
                                    />
                                    <StatCard
                                        label="Open"
                                        value={totals.openCount}
                                        isLoading={isLoading}
                                        gradient="from-sky-500 to-sky-600"
                                        iconBg="bg-sky-400/30"
                                    />
                                    <StatCard
                                        label="Escalated"
                                        value={totals.escalatedCount}
                                        isLoading={isLoading}
                                        gradient={totals.escalatedCount > 0 ? 'from-amber-500 to-orange-600' : 'from-emerald-500 to-emerald-600'}
                                        iconBg={totals.escalatedCount > 0 ? 'bg-amber-400/30' : 'bg-emerald-400/30'}
                                    />
                                    <StatCard
                                        label="SLA Breached"
                                        value={totals.breachedCount}
                                        isLoading={isLoading}
                                        gradient={totals.breachedCount > 0 ? 'from-rose-500 to-red-600' : 'from-emerald-500 to-emerald-600'}
                                        iconBg={totals.breachedCount > 0 ? 'bg-rose-400/30' : 'bg-emerald-400/30'}
                                    />
                                    <StatCard
                                        label="Intervention Rate"
                                        value={interventionRate}
                                        suffix="%"
                                        isLoading={isLoading}
                                        gradient={interventionRate !== null && interventionRate > 0 ? 'from-violet-500 to-purple-600' : 'from-emerald-500 to-emerald-600'}
                                        iconBg={interventionRate !== null && interventionRate > 0 ? 'bg-violet-400/30' : 'bg-emerald-400/30'}
                                        tooltip="% of PR tickets requiring manual engineer escalation"
                                    />
                                </div>

                                <div className="bg-white rounded-xl shadow-sm ring-1 ring-slate-200 overflow-hidden">
                                    <div className="px-6 py-4 flex items-center justify-between border-b border-slate-100">
                                        <div>
                                            <h2 className="text-sm font-semibold text-slate-900">Repositories</h2>
                                            {filteredAndSorted.length > 0 && (
                                                <p className="text-xs text-slate-400 mt-0.5">
                                                    {filteredAndSorted.length === repoCount
                                                        ? `${repoCount} repos`
                                                        : `${filteredAndSorted.length} of ${repoCount} repos`
                                                    }
                                                </p>
                                            )}
                                        </div>
                                        <div className="relative">
                                            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                                            <input
                                                type="text"
                                                value={search}
                                                onChange={(e) => handleSearch(e.target.value)}
                                                placeholder="Filter repos or teams..."
                                                className="pl-9 pr-3 py-1.5 text-sm bg-slate-50 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-300 w-64 placeholder:text-slate-400"
                                            />
                                        </div>
                                    </div>

                                    {error ? (
                                        <div className="p-16 text-center text-red-500 text-sm">Failed to load data — please try again</div>
                                    ) : isLoading ? (
                                        <div className="p-16 text-center text-slate-400 text-sm">Loading...</div>
                                    ) : filteredAndSorted.length === 0 ? (
                                        <div className="p-16 text-center text-slate-400 text-sm">
                                            {search ? 'No repos match your search' : 'No PR data for this period'}
                                        </div>
                                    ) : (
                                        <>
                                        <table className="w-full text-sm">
                                            <thead>
                                                <tr className="text-left text-xs font-medium text-slate-400 uppercase tracking-wider bg-slate-50/80">
                                                    <SortHeader label="Repository" sortKey="repo" align="left" first activeSortKey={sortKey} sortDir={sortDir} onSort={handleSort} />
                                                    <SortHeader label="Team" sortKey="team" align="left" activeSortKey={sortKey} sortDir={sortDir} onSort={handleSort} />
                                                    <SortHeader label="PRs" sortKey="prCount" activeSortKey={sortKey} sortDir={sortDir} onSort={handleSort} />
                                                    <SortHeader label="Open" sortKey="openCount" activeSortKey={sortKey} sortDir={sortDir} onSort={handleSort} />
                                                    <SortHeader label="Escalated" sortKey="escalatedCount" activeSortKey={sortKey} sortDir={sortDir} onSort={handleSort} />
                                                    <SortHeader label="Breached" sortKey="breachedCount" activeSortKey={sortKey} sortDir={sortDir} onSort={handleSort} />
                                                    <SortHeader label="p50" sortKey="p50" activeSortKey={sortKey} sortDir={sortDir} onSort={handleSort} tooltip="50% of PRs are resolved within this time" />
                                                    <SortHeader label="p90" sortKey="p90" activeSortKey={sortKey} sortDir={sortDir} onSort={handleSort} tooltip="90% of PRs are resolved within this time" />
                                                    <SortHeader label="p99" sortKey="p99" last activeSortKey={sortKey} sortDir={sortDir} onSort={handleSort} tooltip="99% of PRs are resolved within this time" />
                                                </tr>
                                            </thead>
                                            <tbody className="divide-y divide-slate-100">
                                                {pagedRepos.map((repo) => (
                                                    <tr key={repo.repo} className="hover:bg-slate-50/70 transition-colors">
                                                        <td className="pl-6 pr-3 py-3.5">
                                                            <div className="flex items-center gap-2">
                                                                <span className="font-medium text-slate-900">{repo.repo}</span>
                                                                {repo.hasSla === false && (
                                                                    <span className="inline-flex items-center px-1.5 py-0.5 rounded text-xs font-medium bg-amber-50 text-amber-700 ring-1 ring-amber-200">
                                                                        No SLA
                                                                    </span>
                                                                )}
                                                            </div>
                                                        </td>
                                                        <td className="px-3 py-3.5 text-slate-500">{repo.owningTeam}</td>
                                                        <td className="px-3 py-3.5 text-right tabular-nums font-medium text-slate-900">{repo.prCount}</td>
                                                        <td className="px-3 py-3.5 text-right tabular-nums text-slate-600">{repo.openCount}</td>
                                                        <td className="px-3 py-3.5 text-right">
                                                            <Badge value={repo.escalatedCount} accent="amber" />
                                                        </td>
                                                        <td className="px-3 py-3.5 text-right">
                                                            {repo.hasSla === false
                                                                ? <span className="text-slate-300 tabular-nums">{'\u2014'}</span>
                                                                : <Badge value={repo.breachedCount} accent="red" />
                                                            }
                                                        </td>
                                                        <td className="px-3 py-3.5 text-right">
                                                            <DurationPill seconds={repo.p50Seconds} />
                                                        </td>
                                                        <td className="px-3 py-3.5 text-right">
                                                            <DurationPill seconds={repo.p90Seconds} />
                                                        </td>
                                                        <td className="px-3 py-3.5 text-right pr-6">
                                                            <DurationPill seconds={repo.p99Seconds} />
                                                        </td>
                                                    </tr>
                                                ))}
                                            </tbody>
                                        </table>

                                        {totalPages > 1 && (
                                            <div className="flex items-center justify-between px-6 py-3 border-t border-slate-100 bg-slate-50/50">
                                                <p className="text-xs text-slate-500">
                                                    {page * pageSize + 1}–{Math.min((page + 1) * pageSize, filteredAndSorted.length)} of {filteredAndSorted.length}
                                                </p>
                                                <div className="flex items-center gap-1">
                                                    <button
                                                        onClick={() => setPage(p => p - 1)}
                                                        disabled={page === 0}
                                                        aria-label="Previous page"
                                                        className="p-1.5 rounded-md hover:bg-slate-200 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                                                    >
                                                        <ChevronLeft className="w-4 h-4 text-slate-600" />
                                                    </button>
                                                    <span className="text-xs text-slate-600 px-2">
                                                        Page {page + 1} of {totalPages}
                                                    </span>
                                                    <button
                                                        onClick={() => setPage(p => p + 1)}
                                                        disabled={page >= totalPages - 1}
                                                        aria-label="Next page"
                                                        className="p-1.5 rounded-md hover:bg-slate-200 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                                                    >
                                                        <ChevronRight className="w-4 h-4 text-slate-600" />
                                                    </button>
                                                </div>
                                            </div>
                                        )}
                                        </>
                                    )}
                                </div>
                            </div>
                        )}

                        {activeTab === 'inflight' && (
                            <InFlightPrsTab />
                        )}
                    </div>
                </div>
            </div>
        </div>
        </Tooltip.Provider>
    )
}

function StatCard({ label, value, suffix, isLoading, gradient, iconBg, tooltip }: {
    label: string
    value: number | null
    suffix?: string
    isLoading: boolean
    gradient: string
    iconBg: string
    tooltip?: string
}) {
    const card = (
        <div className={`relative overflow-hidden rounded-xl bg-gradient-to-br ${gradient} p-5 shadow-sm`}>
            <div className={`absolute -top-4 -right-4 w-24 h-24 rounded-full ${iconBg}`} />
            <div className={`absolute -bottom-6 -right-6 w-20 h-20 rounded-full ${iconBg}`} />
            <div className="relative">
                <p className="text-sm font-medium text-white/80">{label}</p>
                {isLoading ? (
                    <div className="h-9 mt-1 w-16 bg-white/20 rounded animate-pulse" />
                ) : (
                    <p className="text-3xl font-bold text-white mt-1 tabular-nums">
                        {value !== null ? `${value}${suffix ?? ''}` : '—'}
                    </p>
                )}
            </div>
        </div>
    )

    if (!tooltip) return card

    return (
        <Tooltip.Root>
            <Tooltip.Trigger asChild>{card}</Tooltip.Trigger>
            <Tooltip.Portal>
                <Tooltip.Content
                    side="bottom"
                    sideOffset={6}
                    className="z-50 max-w-[220px] px-3 py-2 text-xs leading-relaxed text-white bg-slate-900 rounded-lg shadow-lg animate-in fade-in-0 zoom-in-95"
                >
                    {tooltip}
                    <Tooltip.Arrow className="fill-slate-900" />
                </Tooltip.Content>
            </Tooltip.Portal>
        </Tooltip.Root>
    )
}

function SortHeader({ label, sortKey, activeSortKey, sortDir, onSort, align, first, last, tooltip }: {
    label: string
    sortKey: StatsSortKey
    activeSortKey: StatsSortKey
    sortDir: SortDir
    onSort: (key: StatsSortKey) => void
    align?: 'left' | 'right'
    first?: boolean
    last?: boolean
    tooltip?: string
}) {
    const isActive = activeSortKey === sortKey
    const textAlign = align === 'left' ? 'text-left' : 'text-right'
    const padding = first ? 'pl-6 pr-3' : last ? 'px-3 pr-6' : 'px-3'

    return (
        <th
            className={`${padding} py-3 ${textAlign} cursor-pointer select-none hover:text-slate-600 transition-colors group`}
            onClick={() => onSort(sortKey)}
        >
            <span className="inline-flex items-center gap-1">
                {align === 'left' ? label : null}
                <span className={`inline-flex flex-col ${isActive ? 'text-indigo-500' : 'text-slate-300 opacity-0 group-hover:opacity-100'} transition-opacity`}>
                    <ChevronUp className={`w-3 h-3 -mb-1 ${isActive && sortDir === 'asc' ? 'text-indigo-500' : ''}`} />
                    <ChevronDown className={`w-3 h-3 ${isActive && sortDir === 'desc' ? 'text-indigo-500' : ''}`} />
                </span>
                {align !== 'left' ? label : null}
                {tooltip && (
                    <Tooltip.Root>
                        <Tooltip.Trigger asChild onClick={(e: React.MouseEvent) => e.stopPropagation()}>
                            <Info className="w-3 h-3 text-slate-300 hover:text-indigo-400 cursor-help transition-colors" />
                        </Tooltip.Trigger>
                        <Tooltip.Portal>
                            <Tooltip.Content
                                side="bottom"
                                sideOffset={6}
                                className="z-50 max-w-[200px] px-3 py-2 text-xs leading-relaxed text-white bg-slate-900 rounded-lg shadow-lg animate-in fade-in-0 zoom-in-95"
                            >
                                {tooltip}
                                <Tooltip.Arrow className="fill-slate-900" />
                            </Tooltip.Content>
                        </Tooltip.Portal>
                    </Tooltip.Root>
                )}
            </span>
        </th>
    )
}

function Badge({ value, accent }: { value: number; accent: 'amber' | 'red' }) {
    if (value === 0) return <span className="text-slate-300 tabular-nums">0</span>

    const style = accent === 'red'
        ? 'bg-red-50 text-red-700 ring-1 ring-red-600/20'
        : 'bg-amber-50 text-amber-700 ring-1 ring-amber-600/20'

    return (
        <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold ${style}`}>
            {value}
        </span>
    )
}

function DurationPill({ seconds }: { seconds: number }) {
    return (
        <span className={`inline-block px-2 py-0.5 rounded-md text-xs font-medium tabular-nums ring-1 ${durationStyle(seconds)}`}>
            {formatDuration(seconds)}
        </span>
    )
}
