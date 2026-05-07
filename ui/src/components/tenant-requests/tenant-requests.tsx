'use client'

import React, { useEffect, useMemo, useState } from 'react'
import { ArrowUp, ArrowDown, ArrowUpDown, Search, Info, BarChart3, Eye } from 'lucide-react'
import { useTenantInsightsStats, useEscalationBreakdown } from '@/lib/hooks'
import { useUrlParams, enumValidator, isoDateValidator } from '@/lib/hooks/useUrlParams'
import { getDateRangeFromFilter, PRESET_DAYS } from '@/lib/dateRange'
import { formatDuration } from '@/lib/utils/format'
import type { RepoInsights } from '@/lib/types/dashboard'
import InFlightPrsTab from './in-flight-prs'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { Badge } from '@/components/ui/badge'

type StatsSortKey = 'severity' | 'repo' | 'team' | 'prCount' | 'openCount' | 'escalatedCount' | 'breachedCount' | 'p50' | 'p90' | 'p99'
type SortDir = 'asc' | 'desc'

function durationStyle(seconds: number): string {
 if (seconds < 14400) return 'text-success bg-success/10'
 if (seconds < 86400) return 'text-warning bg-warning/10'
 return 'text-destructive bg-destructive/10'
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
 { key: 'stats' as const, label: 'PR Activity & SLA Health', icon: BarChart3 },
 { key: 'inflight' as const, label: 'In-Flight PRs', icon: Eye },
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
 <TooltipProvider delayDuration={200}>
 <div className="space-y-6">
 <div className="flex items-start justify-between gap-4">
 <div>
 <h1 className="text-2xl font-bold text-foreground">Tenant Requests</h1>
 <p className="text-muted-foreground text-sm">PR tracking, review lifecycle, and SLA health</p>
 </div>
 {activeTab === 'stats' && (
 <div className="flex flex-wrap items-center gap-2">
 <Select
 value={dateFilter}
 onValueChange={(v) => {
 if (v !== 'custom') {
 setParams({ dateFilter: v, dateFrom: '', dateTo: '' })
 } else {
 setParams({ dateFilter: 'custom' })
 }
 setPage(0)
 }}
 >
 <SelectTrigger className="w-[160px]"><SelectValue /></SelectTrigger>
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
 <Input
 type="date"
 aria-label="Date filter start"
 value={params.dateFrom}
 onChange={e => { setParams({ dateFrom: e.target.value }); setPage(0) }}
 className="w-[150px]"
 />
 <Input
 type="date"
 aria-label="Date filter end"
 value={params.dateTo}
 onChange={e => { setParams({ dateTo: e.target.value }); setPage(0) }}
 className="w-[150px]"
 />
 </>
 )}
 {dateFilter === 'custom' && !isDateRangeValid && (
 <span className="text-xs text-destructive font-medium">Invalid range</span>
 )}
 </div>
 )}
 </div>

 <Tabs value={activeTab} onValueChange={(v) => setParams({ tab: v })} className="space-y-4">
 <TabsList>
 {tabs.map((tab) => {
 const Icon = tab.icon
 return (
 <TabsTrigger key={tab.key} value={tab.key} className="cursor-pointer">
 <Icon className="h-4 w-4" />
 {tab.label}
 </TabsTrigger>
 )
 })}
 </TabsList>

 <TabsContent value="stats" className="space-y-6">
 <div>
 <h2 className="text-base font-semibold text-foreground">PR Activity & SLA Health</h2>
 <p className="text-sm text-muted-foreground">Pull request tracking across repositories</p>
 </div>

 <div className="grid grid-cols-2 lg:grid-cols-7 gap-4">
 <StatCard label="Repositories" value={repoCount} isLoading={isLoading} accent="primary" />
 <StatCard
 label="No SLA Repos"
 value={noSlaRepoCount}
 isLoading={isLoading}
 valueClass={noSlaRepoCount > 0 ? 'text-warning' : undefined}
 accent="warning"
 />
 <StatCard label="Total PRs" value={totals.prCount} isLoading={isLoading} accent="info" />
 <StatCard label="Open" value={totals.openCount} isLoading={isLoading} accent="success" />
 <StatCard
 label="Escalated"
 value={totals.escalatedCount}
 isLoading={isLoading}
 valueClass={totals.escalatedCount > 0 ? 'text-warning' : undefined}
 accent="purple"
 />
 <StatCard
 label="SLA Breached"
 value={totals.breachedCount}
 isLoading={isLoading}
 valueClass={totals.breachedCount > 0 ? 'text-destructive' : undefined}
 accent="destructive"
 />
 <StatCard
 label="Intervention Rate"
 value={interventionRate}
 suffix="%"
 isLoading={isLoading}
 valueClass={interventionRate !== null && interventionRate > 0 ? 'text-warning' : undefined}
 accent="indigo"
 tooltip="% of PR tickets requiring manual engineer escalation"
 />
 </div>

 <div className="rounded-xl border bg-card">
 <div className="px-6 py-4 flex items-center justify-between border-b">
 <div>
 <h2 className="text-base font-semibold text-foreground">Repositories</h2>
 {filteredAndSorted.length > 0 && (
 <p className="text-xs text-muted-foreground mt-0.5">
 {filteredAndSorted.length === repoCount
 ? `${repoCount} repos`
 : `${filteredAndSorted.length} of ${repoCount} repos`
 }
 </p>
 )}
 </div>
 <div className="relative">
 <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
 <Input
 type="text"
 value={search}
 onChange={(e) => handleSearch(e.target.value)}
 placeholder="Filter repos or teams..."
 className="pl-9 w-64"
 />
 </div>
 </div>

 {error ? (
 <div className="p-16 text-center text-destructive text-sm">Failed to load data — please try again</div>
 ) : isLoading ? (
 <div className="p-16 text-center text-muted-foreground text-sm">Loading...</div>
 ) : filteredAndSorted.length === 0 ? (
 <div className="p-16 text-center text-muted-foreground text-sm">
 {search ? 'No repos match your search' : 'No PR data for this period'}
 </div>
 ) : (
 <>
 <div className="overflow-x-auto">
 <table className="min-w-full divide-y">
 <thead className="bg-muted">
 <tr>
 <SortHeader label="Repository" sortKey="repo" align="left" activeSortKey={sortKey} sortDir={sortDir} onSort={handleSort} />
 <SortHeader label="Team" sortKey="team" align="left" activeSortKey={sortKey} sortDir={sortDir} onSort={handleSort} />
 <SortHeader label="PRs" sortKey="prCount" align="right" activeSortKey={sortKey} sortDir={sortDir} onSort={handleSort} />
 <SortHeader label="Open" sortKey="openCount" align="right" activeSortKey={sortKey} sortDir={sortDir} onSort={handleSort} />
 <SortHeader label="Escalated" sortKey="escalatedCount" align="right" activeSortKey={sortKey} sortDir={sortDir} onSort={handleSort} />
 <SortHeader label="Breached" sortKey="breachedCount" align="right" activeSortKey={sortKey} sortDir={sortDir} onSort={handleSort} />
 <SortHeader label="p50" sortKey="p50" align="right" activeSortKey={sortKey} sortDir={sortDir} onSort={handleSort} tooltip="50% of PRs are resolved within this time" />
 <SortHeader label="p90" sortKey="p90" align="right" activeSortKey={sortKey} sortDir={sortDir} onSort={handleSort} tooltip="90% of PRs are resolved within this time" />
 <SortHeader label="p99" sortKey="p99" align="right" activeSortKey={sortKey} sortDir={sortDir} onSort={handleSort} tooltip="99% of PRs are resolved within this time" />
 </tr>
 </thead>
 <tbody className="divide-y">
 {pagedRepos.map((repo) => (
 <tr key={repo.repo} className="hover:bg-accent transition-colors">
 <td className="px-4 py-2 text-sm">
 <div className="flex items-center gap-2">
 <span className="font-medium text-foreground">{repo.repo}</span>
 {repo.hasSla === false && (
 <Badge variant="outline" className="text-warning border-warning/40">No SLA</Badge>
 )}
 </div>
 </td>
 <td className="px-4 py-2 text-sm text-muted-foreground">{repo.owningTeam}</td>
 <td className="px-4 py-2 text-right tabular-nums font-mono text-sm text-foreground">{repo.prCount}</td>
 <td className="px-4 py-2 text-right tabular-nums font-mono text-sm text-muted-foreground">{repo.openCount}</td>
 <td className="px-4 py-2 text-right">
 <CountBadge value={repo.escalatedCount} accent="warning" />
 </td>
 <td className="px-4 py-2 text-right">
 {repo.hasSla === false
 ? <span className="text-muted-foreground/50 tabular-nums">{'—'}</span>
 : <CountBadge value={repo.breachedCount} accent="destructive" />
 }
 </td>
 <td className="px-4 py-2 text-right">
 <DurationPill seconds={repo.p50Seconds} />
 </td>
 <td className="px-4 py-2 text-right">
 <DurationPill seconds={repo.p90Seconds} />
 </td>
 <td className="px-4 py-2 text-right">
 <DurationPill seconds={repo.p99Seconds} />
 </td>
 </tr>
 ))}
 </tbody>
 </table>
 </div>

 {totalPages > 1 && (
 <div className="flex items-center justify-end gap-4 px-6 py-3 border-t">
 <span className="text-sm text-muted-foreground">
 Page {page + 1} of {totalPages}
 </span>
 <Button
 variant="outline"
 size="sm"
 onClick={() => setPage(p => p - 1)}
 disabled={page === 0}
 >
 Previous
 </Button>
 <Button
 variant="outline"
 size="sm"
 onClick={() => setPage(p => p + 1)}
 disabled={page >= totalPages - 1}
 >
 Next
 </Button>
 </div>
 )}
 </>
 )}
 </div>
 </TabsContent>

 <TabsContent value="inflight">
 <InFlightPrsTab />
 </TabsContent>
 </Tabs>
 </div>
 </TooltipProvider>
 )
}

function StatCard({ label, value, suffix, isLoading, valueClass, accent = 'primary', tooltip }: {
 label: string
 value: number | null
 suffix?: string
 isLoading: boolean
 valueClass?: string
 accent?: 'primary' | 'warning' | 'destructive' | 'success' | 'info' | 'purple' | 'indigo'
 tooltip?: string
}) {
 const accentBg: Record<NonNullable<typeof accent>, string> = {
 primary: 'bg-primary/15',
 warning: 'bg-warning/15',
 destructive: 'bg-destructive/15',
 success: 'bg-success/15',
 info: 'bg-info/15',
 purple: 'bg-chart-4/15',
 indigo: 'bg-chart-9/15',
 }
 const circleClass = accentBg[accent]

 const card = (
 <div className="relative overflow-hidden rounded-xl border bg-card p-6">
 <div className={`absolute -top-4 -right-4 w-24 h-24 rounded-full ${circleClass}`} />
 <div className={`absolute -bottom-6 -right-6 w-20 h-20 rounded-full ${circleClass}`} />
 <div className="relative">
 <div className="flex items-center gap-2 mb-2">
 <p className="text-sm font-medium text-muted-foreground">{label}</p>
 {tooltip && <Info className="h-3.5 w-3.5 text-muted-foreground/70" />}
 </div>
 {isLoading ? (
 <div className="h-9 w-16 bg-muted rounded animate-pulse" />
 ) : (
 <p className={`font-mono text-3xl font-semibold tracking-tight tabular-nums ${valueClass ?? 'text-foreground'}`}>
 {value !== null ? `${value}${suffix ?? ''}` : '—'}
 </p>
 )}
 </div>
 </div>
 )

 if (!tooltip) return card

 return (
 <Tooltip>
 <TooltipTrigger asChild>{card}</TooltipTrigger>
 <TooltipContent side="bottom" sideOffset={6} className="max-w-[220px]">
 {tooltip}
 </TooltipContent>
 </Tooltip>
 )
}

function SortHeader({ label, sortKey, activeSortKey, sortDir, onSort, align, tooltip }: {
 label: string
 sortKey: StatsSortKey
 activeSortKey: StatsSortKey
 sortDir: SortDir
 onSort: (key: StatsSortKey) => void
 align: 'left' | 'right'
 tooltip?: string
}) {
 const isActive = activeSortKey === sortKey
 const textAlign = align === 'left' ? 'text-left' : 'text-right'

 return (
 <th
 className={`px-4 py-2 ${textAlign} text-xs font-bold text-foreground uppercase cursor-pointer select-none hover:bg-muted/70 transition-colors`}
 onClick={() => onSort(sortKey)}
 >
 <span className={`inline-flex items-center gap-1 ${align === 'right' ? 'justify-end w-full' : ''}`}>
 {label}
 {isActive
 ? (sortDir === 'asc' ? <ArrowUp className="h-3.5 w-3.5" /> : <ArrowDown className="h-3.5 w-3.5" />)
 : <ArrowUpDown className="h-3.5 w-3.5 text-muted-foreground" />}
 {tooltip && (
 <Tooltip>
 <TooltipTrigger asChild onClick={(e: React.MouseEvent) => e.stopPropagation()}>
 <Info className="h-3 w-3 text-muted-foreground hover:text-foreground transition-colors" />
 </TooltipTrigger>
 <TooltipContent side="bottom" sideOffset={6} className="max-w-[200px]">
 {tooltip}
 </TooltipContent>
 </Tooltip>
 )}
 </span>
 </th>
 )
}

function CountBadge({ value, accent }: { value: number; accent: 'warning' | 'destructive' }) {
 if (value === 0) return <span className="text-muted-foreground/50 tabular-nums font-mono text-sm">0</span>

 const style = accent === 'destructive'
 ? 'bg-destructive/10 text-destructive'
 : 'bg-warning/10 text-warning'

 return (
 <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold tabular-nums ${style}`}>
 {value}
 </span>
 )
}

function DurationPill({ seconds }: { seconds: number }) {
 return (
 <span className={`inline-block px-2 py-0.5 rounded-md text-xs font-medium tabular-nums ${durationStyle(seconds)}`}>
 {formatDuration(seconds)}
 </span>
 )
}
