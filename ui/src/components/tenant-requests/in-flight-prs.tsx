'use client'

import React, { useEffect, useMemo, useState } from 'react'
import { ChevronUp, ChevronDown, ChevronLeft, ChevronRight, Search, ExternalLink, HelpCircle } from 'lucide-react'
import * as Tooltip from '@radix-ui/react-tooltip'
import { useInFlightPrs } from '@/lib/hooks'
import type { InFlightPr } from '@/lib/types/dashboard'

type SortKey = 'severity' | 'pr' | 'status' | 'waitingOn' | 'sla' | 'age' | 'lastReview' | 'team'
type SortDir = 'asc' | 'desc'

const STATUS_SEVERITY: Record<string, number> = {
    ESCALATED: 0,
    OPEN: 1,
    CHANGES_REQUESTED: 2,
    APPROVED: 3,
}

function statusSeverity(status: string): number {
    return STATUS_SEVERITY[status] ?? 99
}

function statusBadgeStyle(status: string): string {
    switch (status) {
        case 'OPEN': return 'bg-blue-50 text-blue-700 ring-1 ring-blue-600/20'
        case 'CHANGES_REQUESTED': return 'bg-amber-50 text-amber-700 ring-1 ring-amber-600/20'
        case 'APPROVED': return 'bg-emerald-50 text-emerald-700 ring-1 ring-emerald-600/20'
        case 'ESCALATED': return 'bg-red-50 text-red-700 ring-1 ring-red-600/20'
        default: return 'bg-slate-50 text-slate-700 ring-1 ring-slate-600/20'
    }
}

function statusLabel(status: string): string {
    switch (status) {
        case 'CHANGES_REQUESTED': return 'Changes Requested'
        default: return status.charAt(0) + status.slice(1).toLowerCase()
    }
}

function slackDeepLink(channelId: string, queryTs: string): string {
    return `https://slack.com/archives/${channelId}/p${queryTs.replace('.', '')}`
}

function ageFromNow(isoDate: string): { totalSeconds: number; label: string } {
    const diff = (Date.now() - new Date(isoDate).getTime()) / 1000
    const days = Math.floor(diff / 86400)
    const hours = Math.floor((diff % 86400) / 3600)
    return { totalSeconds: diff, label: days > 0 ? `${days}d ${hours}h` : `${hours}h` }
}

function relativeTime(isoDate: string | null): string {
    if (!isoDate) return '\u2014'
    const diff = (Date.now() - new Date(isoDate).getTime()) / 1000
    const days = Math.floor(diff / 86400)
    const hours = Math.floor((diff % 86400) / 3600)
    if (days > 0) return `${days}d ${hours}h ago`
    if (hours > 0) return `${hours}h ago`
    const mins = Math.floor(diff / 60)
    return `${mins}m ago`
}

interface SlaInfo {
    label: string
    style: string
    sortValue: number // lower = more urgent
}

/**
 * Single source of truth for how this component interprets a PR's SLA state. Consumers (row
 * badge via slaInfo, totals aggregation, data-integrity warnings) all route through this
 * classifier so that a row rendered as "Breached" in the table is guaranteed to also count
 * toward the Breached stat card — they read the same discriminant, not three similar-but-
 * slightly-different boolean chains.
 *
 * hasSla=undefined (wire drift / older API without the V15 field) is classified as 'unknown'
 * rather than collapsed to 'none' — mapping "missing" onto "No SLA" would silently hide
 * breaches behind the amber badge. `slaInfo` maps 'unknown' onto the SLA path (deadline/
 * paused/breached based on the other fields), matching the component's pre-classifier
 * behaviour and keeping breached PRs visible.
 */
type SlaState =
    | { kind: 'none' }
    | { kind: 'unknown' }
    | { kind: 'paused'; remainingSeconds: number }
    | { kind: 'active'; deadlineMs: number; remainingSec: number }
    | { kind: 'breached'; deadlineMs: number; remainingSec: number }
    | { kind: 'missing' } // hasSla-true/unknown but both deadline fields null

function classifySla(pr: InFlightPr, now: number): SlaState {
    if (pr.hasSla === false) {
        return { kind: 'none' }
    }
    const unknownGate: 'unknown' | 'active' | 'paused' | 'missing' | 'breached' =
        pr.hasSla === undefined ? 'unknown' : 'active'
    if (pr.slaRemainingSeconds != null && pr.slaDeadline == null) {
        return { kind: 'paused', remainingSeconds: pr.slaRemainingSeconds }
    }
    if (pr.slaDeadline != null) {
        const deadlineMs = new Date(pr.slaDeadline).getTime()
        const remainingSec = (deadlineMs - now) / 1000
        return remainingSec > 0
            ? { kind: 'active', deadlineMs, remainingSec }
            : { kind: 'breached', deadlineMs, remainingSec }
    }
    // Fell through every SLA-shaped branch with hasSla true-or-unknown: both date fields null.
    // unknownGate kept so a future refactor can route 'unknown + both-null' differently if we
    // ever want to bias toward "assume no SLA" under skew. For now both collapse to 'missing'.
    void unknownGate
    return { kind: 'missing' }
}

function slaInfo(pr: InFlightPr, now: number): SlaInfo {
    const state = classifySla(pr, now)
    switch (state.kind) {
        case 'none':
            return { label: 'No SLA', style: 'bg-amber-50 text-amber-700 ring-1 ring-amber-200', sortValue: 9999 }
        case 'paused': {
            const hours = Math.max(0, state.remainingSeconds / 3600)
            return {
                label: `Paused (${hours.toFixed(1)}h remaining)`,
                style: 'text-slate-600',
                sortValue: 1000 + state.remainingSeconds,
            }
        }
        case 'active': {
            const hours = state.remainingSec / 3600
            const style = hours > 4
                ? 'text-emerald-700 bg-emerald-50 ring-1 ring-emerald-200'
                : 'text-amber-700 bg-amber-50 ring-1 ring-amber-200'
            return { label: `${hours.toFixed(1)}h left`, style, sortValue: state.remainingSec }
        }
        case 'breached': {
            const daysAgo = Math.floor(Math.abs(state.remainingSec) / 86400)
            return {
                label: `Breached ${daysAgo > 0 ? `${daysAgo}d ago` : 'today'}`,
                style: 'text-red-700 bg-red-50 ring-1 ring-red-200',
                sortValue: state.remainingSec,
            }
        }
        case 'unknown':
        case 'missing':
            // Sort to the bottom (MAX_SAFE_INTEGER) so these don't falsely read as "most urgent" —
            // the Breached stat card can't classify them either, and sorting them to the top would
            // create a visible stat-vs-rows mismatch. Diagnostics are emitted once per data refresh
            // via a useEffect in the component, not here, to avoid spamming the console for every
            // render/sort pass.
            return {
                label: 'SLA data missing',
                style: 'text-slate-600 bg-slate-100 ring-1 ring-slate-300',
                sortValue: Number.MAX_SAFE_INTEGER,
            }
    }
}

function compareByKey(a: InFlightPr, b: InFlightPr, key: SortKey, dir: SortDir, now: number): number {
    let cmp = 0
    switch (key) {
        case 'severity': cmp = statusSeverity(a.status) - statusSeverity(b.status); break
        case 'pr': cmp = a.githubRepo.localeCompare(b.githubRepo) || a.prNumber - b.prNumber; break
        case 'status': cmp = statusSeverity(a.status) - statusSeverity(b.status); break
        case 'waitingOn': cmp = a.waitingOn.localeCompare(b.waitingOn); break
        case 'sla': cmp = slaInfo(a, now).sortValue - slaInfo(b, now).sortValue; break
        case 'age': {
            const ageA = new Date(a.prCreatedAt).getTime()
            const ageB = new Date(b.prCreatedAt).getTime()
            cmp = ageA - ageB // older first when desc
            break
        }
        case 'lastReview': {
            const la = a.lastReviewAt ? new Date(a.lastReviewAt).getTime() : 0
            const lb = b.lastReviewAt ? new Date(b.lastReviewAt).getTime() : 0
            cmp = la - lb
            break
        }
        case 'team': cmp = a.owningTeamLabel.localeCompare(b.owningTeamLabel); break
        default: key satisfies never
    }
    return dir === 'desc' ? -cmp : cmp
}

function repoShortName(fullRepo: string): string {
    const parts = fullRepo.split('/')
    return parts.length > 1 ? parts[parts.length - 1] : fullRepo
}

export default function InFlightPrsTab() {
    const [teamFilter, setTeamFilter] = useState<string>('')
    const { data: allPrs, isLoading, error } = useInFlightPrs()
    // Stabilize to avoid a fresh [] identity on every render while data is loading,
    // which would invalidate every downstream useMemo/useEffect unnecessarily.
    const unfilteredPrs = useMemo(() => allPrs ?? [], [allPrs])

    // Derive team list from ALL data, not filtered
    const teams = useMemo(() => {
        const set = new Map<string, string>()
        unfilteredPrs.forEach(pr => set.set(pr.owningTeam, pr.owningTeamLabel))
        return Array.from(set.entries()).sort((a, b) => a[1].localeCompare(b[1]))
    }, [unfilteredPrs])

    // Filter by team client-side
    const prs = useMemo(
        () => teamFilter ? unfilteredPrs.filter(pr => pr.owningTeam === teamFilter) : unfilteredPrs,
        [unfilteredPrs, teamFilter]
    )

    const [search, setSearch] = useState('')
    const [sortKey, setSortKey] = useState<SortKey>('severity')
    const [sortDir, setSortDir] = useState<SortDir>('desc')
    const [page, setPage] = useState(0)
    const pageSize = 20

    // Clock tick: re-computed when data refreshes AND every minute so PRs crossing their
    // deadline mid-session re-classify. Shared across totals stat cards and table badges to keep
    // them in sync within a render. Interval is mounted once; dataTimestamp re-derives from data
    // OR tick, avoiding setState-in-effect loops if upstream identities aren't stable.
    const [clockTick, setClockTick] = useState(0)
    useEffect(() => {
        const id = setInterval(() => setClockTick(t => t + 1), 60_000)
        return () => clearInterval(id)
    }, [])
    // Intentional: re-derive a wall-clock timestamp when data refreshes OR the minute tick fires.
    // eslint-disable-next-line react-hooks/purity
    const dataTimestamp = useMemo(() => Date.now(), [prs, clockTick])

    // Data-integrity warnings — emitted once per data refresh, not per render/sort pass.
    // Uses the same classifier as the row badge and totals: a row we count as 'unknown' here
    // is the same row that gets the "SLA data missing" badge in the table and sorts to the
    // bottom, so a divergence in gating between diagnostic and UI can't happen.
    useEffect(() => {
        const classified = prs.map(p => ({ pr: p, state: classifySla(p, dataTimestamp) }))
        const missingHasSla = classified
            .filter(c => c.pr.hasSla === undefined)
            .map(c => `${c.pr.githubRepo}#${c.pr.prNumber}`)
        if (missingHasSla.length > 0) {
            console.warn(
                `[in-flight-prs] ${missingHasSla.length} PR(s) missing hasSla — likely API/UI version skew: ${missingHasSla.join(', ')}`
            )
        }
        const brokenSla = classified
            .filter(c => c.state.kind === 'missing')
            .map(c => `${c.pr.githubRepo}#${c.pr.prNumber}`)
        if (brokenSla.length > 0) {
            console.error(
                `[in-flight-prs] ${brokenSla.length} PR(s) have hasSla!=false but both SLA fields are null: ${brokenSla.join(', ')}`
            )
        }
    }, [prs, dataTimestamp])

    const totals = useMemo(() => {
        let waitingOnTeam = 0
        let waitingOnTenant = 0
        let waitingOnMerge = 0
        let breached = 0
        let noSla = 0
        for (const pr of prs) {
            if (pr.waitingOn === 'TEAM') waitingOnTeam++
            else if (pr.waitingOn === 'TENANT') waitingOnTenant++
            else if (pr.waitingOn === 'MERGE') waitingOnMerge++
            const state = classifySla(pr, dataTimestamp)
            if (state.kind === 'breached') breached++
            if (state.kind === 'none') noSla++
        }
        return { total: prs.length, waitingOnTeam, waitingOnTenant, waitingOnMerge, breached, noSla }
    }, [prs, dataTimestamp])

    const filteredAndSorted = useMemo(() => {
        const q = search.toLowerCase()
        const filtered = q
            ? prs.filter(pr =>
                pr.githubRepo.toLowerCase().includes(q) ||
                pr.owningTeamLabel.toLowerCase().includes(q) ||
                pr.status.toLowerCase().includes(q) ||
                String(pr.prNumber).includes(q)
            )
            : prs
        return [...filtered].sort((a, b) => compareByKey(a, b, sortKey, sortDir, dataTimestamp))
    }, [prs, search, sortKey, sortDir, dataTimestamp])

    const totalPages = Math.ceil(filteredAndSorted.length / pageSize)
    const pagedPrs = filteredAndSorted.slice(page * pageSize, (page + 1) * pageSize)

    const handleSort = (key: SortKey) => {
        setPage(0)
        if (sortKey === key) {
            setSortDir(d => d === 'desc' ? 'asc' : 'desc')
        } else {
            setSortKey(key)
            setSortDir(key === 'pr' || key === 'team' || key === 'waitingOn' ? 'asc' : 'desc')
        }
    }

    const handleSearch = (value: string) => {
        setSearch(value)
        setPage(0)
    }

    return (
        <Tooltip.Provider delayDuration={200}>
        <div className="space-y-6">
            <div className="flex items-center justify-between">
                <div>
                    <h2 className="text-sm font-semibold text-slate-900">In-Flight PRs</h2>
                    <p className="text-xs text-slate-400 mt-0.5">Currently open pull requests across tracked repositories</p>
                </div>
                <div>
                    <select
                        value={teamFilter}
                        onChange={e => { setTeamFilter(e.target.value); setPage(0) }}
                        className="px-3 py-1.5 text-sm bg-white border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-300"
                    >
                        <option value="">All Teams</option>
                        {teams.map(([code, label]) => (
                            <option key={code} value={code}>{label}</option>
                        ))}
                    </select>
                </div>
            </div>

            <div className="grid grid-cols-2 lg:grid-cols-6 gap-4">
                <StatCard
                    label="Total In-Flight"
                    value={totals.total}
                    isLoading={isLoading}
                    gradient="from-indigo-500 to-indigo-600"
                    iconBg="bg-indigo-400/30"
                />
                <StatCard
                    label="Waiting on Team"
                    value={totals.waitingOnTeam}
                    isLoading={isLoading}
                    gradient="from-sky-500 to-sky-600"
                    iconBg="bg-sky-400/30"
                />
                <StatCard
                    label="Waiting on Tenant"
                    value={totals.waitingOnTenant}
                    isLoading={isLoading}
                    gradient="from-slate-600 to-slate-700"
                    iconBg="bg-slate-500/30"
                />
                <StatCard
                    label="Waiting on Merge"
                    value={totals.waitingOnMerge}
                    isLoading={isLoading}
                    gradient="from-emerald-500 to-emerald-600"
                    iconBg="bg-emerald-400/30"
                />
                <StatCard
                    label="No SLA"
                    value={totals.noSla}
                    isLoading={isLoading}
                    gradient="from-amber-500 to-amber-600"
                    iconBg="bg-amber-400/30"
                />
                <StatCard
                    label="SLA Breached"
                    value={totals.breached}
                    isLoading={isLoading}
                    gradient={totals.breached > 0 ? 'from-rose-500 to-red-600' : 'from-emerald-500 to-emerald-600'}
                    iconBg={totals.breached > 0 ? 'bg-rose-400/30' : 'bg-emerald-400/30'}
                />
            </div>

            <div className="bg-white rounded-xl shadow-sm ring-1 ring-slate-200 overflow-hidden">
                <div className="px-6 py-4 flex items-center justify-between border-b border-slate-100">
                    <div>
                        <h2 className="text-sm font-semibold text-slate-900">Pull Requests</h2>
                        {filteredAndSorted.length > 0 && (
                            <p className="text-xs text-slate-400 mt-0.5">
                                {filteredAndSorted.length === prs.length
                                    ? `${prs.length} PRs`
                                    : `${filteredAndSorted.length} of ${prs.length} PRs`
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
                            placeholder="Filter PRs, repos or teams..."
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
                        {search ? 'No PRs match your search' : 'No in-flight PRs'}
                    </div>
                ) : (
                    <>
                    <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                        <thead>
                            <tr className="text-left text-xs font-medium text-slate-400 uppercase tracking-wider bg-slate-50/80">
                                <SortHeader label="PR" sortKey="pr" align="left" first activeSortKey={sortKey} sortDir={sortDir} onSort={handleSort} />
                                <SortHeader label="Status" sortKey="status" align="left" activeSortKey={sortKey} sortDir={sortDir} onSort={handleSort} />
                                <SortHeader label="Waiting On" sortKey="waitingOn" align="left" activeSortKey={sortKey} sortDir={sortDir} onSort={handleSort} />
                                <SortHeader label="SLA" sortKey="sla" activeSortKey={sortKey} sortDir={sortDir} onSort={handleSort} />
                                <SortHeader label="Age" sortKey="age" activeSortKey={sortKey} sortDir={sortDir} onSort={handleSort} />
                                <SortHeader label="Last Review" sortKey="lastReview" activeSortKey={sortKey} sortDir={sortDir} onSort={handleSort} />
                                <SortHeader label="Team" sortKey="team" align="left" activeSortKey={sortKey} sortDir={sortDir} onSort={handleSort} />
                                <th className="px-3 py-3 pr-6 text-right">Slack</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-100">
                            {pagedPrs.map((pr) => {
                                const age = ageFromNow(pr.prCreatedAt)
                                const sla = slaInfo(pr, dataTimestamp)
                                return (
                                    <tr key={`${pr.githubRepo}-${pr.prNumber}`} className="hover:bg-slate-50/70 transition-colors">
                                        <td className="pl-6 pr-3 py-3.5">
                                            <a
                                                href={pr.prUrl}
                                                target="_blank"
                                                rel="noopener noreferrer"
                                                className="inline-flex items-center gap-1.5 font-medium text-indigo-600 hover:text-indigo-800 transition-colors"
                                            >
                                                {repoShortName(pr.githubRepo)}#{pr.prNumber}
                                                <ExternalLink className="w-3 h-3" />
                                            </a>
                                        </td>
                                        <td className="px-3 py-3.5">
                                            <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold ${statusBadgeStyle(pr.status)}`}>
                                                {statusLabel(pr.status)}
                                            </span>
                                        </td>
                                        <td className="px-3 py-3.5 text-slate-600">{pr.waitingOn}</td>
                                        <td className="px-3 py-3.5 text-right">
                                            <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-xs font-medium tabular-nums ${sla.style}`}>
                                                {sla.label}
                                                {sla.label.startsWith('Paused') && (
                                                    <Tooltip.Root>
                                                        <Tooltip.Trigger asChild>
                                                            <HelpCircle className="w-3 h-3 text-slate-400 cursor-help" />
                                                        </Tooltip.Trigger>
                                                        <Tooltip.Portal>
                                                            <Tooltip.Content
                                                                className="z-50 max-w-[220px] px-3 py-2 text-xs leading-relaxed text-white bg-slate-900 rounded-lg shadow-lg"
                                                                sideOffset={5}
                                                            >
                                                                SLA clock is paused because the owning team has reviewed. It will resume when the tenant pushes updates.
                                                                <Tooltip.Arrow className="fill-slate-900" />
                                                            </Tooltip.Content>
                                                        </Tooltip.Portal>
                                                    </Tooltip.Root>
                                                )}
                                            </span>
                                        </td>
                                        <td className="px-3 py-3.5 text-right tabular-nums text-slate-600">{age.label}</td>
                                        <td className="px-3 py-3.5 text-right text-slate-500">{relativeTime(pr.lastReviewAt)}</td>
                                        <td className="px-3 py-3.5 text-slate-500">{pr.owningTeamLabel}</td>
                                        <td className="px-3 py-3.5 text-right pr-6">
                                            {pr.ticketChannelId && pr.ticketQueryTs ? (
                                                <a
                                                    href={slackDeepLink(pr.ticketChannelId, pr.ticketQueryTs)}
                                                    target="_blank"
                                                    rel="noopener noreferrer"
                                                    className="inline-flex items-center gap-1 text-xs text-indigo-600 hover:text-indigo-800 transition-colors"
                                                >
                                                    Thread
                                                    <ExternalLink className="w-3 h-3" />
                                                </a>
                                            ) : (
                                                <span className="text-slate-300">{'\u2014'}</span>
                                            )}
                                        </td>
                                    </tr>
                                )
                            })}
                        </tbody>
                    </table>
                    </div>

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
        </Tooltip.Provider>
    )
}

function StatCard({ label, value, isLoading, gradient, iconBg }: {
    label: string
    value: number
    isLoading: boolean
    gradient: string
    iconBg: string
}) {
    return (
        <div className={`relative overflow-hidden rounded-xl bg-gradient-to-br ${gradient} p-5 shadow-sm`}>
            <div className={`absolute -top-4 -right-4 w-24 h-24 rounded-full ${iconBg}`} />
            <div className={`absolute -bottom-6 -right-6 w-20 h-20 rounded-full ${iconBg}`} />
            <div className="relative">
                <p className="text-sm font-medium text-white/80">{label}</p>
                {isLoading ? (
                    <div className="h-9 mt-1 w-16 bg-white/20 rounded animate-pulse" />
                ) : (
                    <p className="font-mono text-3xl font-semibold tracking-tight text-white mt-1 tabular-nums">{value}</p>
                )}
            </div>
        </div>
    )
}

function SortHeader({ label, sortKey, activeSortKey, sortDir, onSort, align, first, last }: {
    label: string
    sortKey: SortKey
    activeSortKey: SortKey
    sortDir: SortDir
    onSort: (key: SortKey) => void
    align?: 'left' | 'right'
    first?: boolean
    last?: boolean
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
            </span>
        </th>
    )
}
