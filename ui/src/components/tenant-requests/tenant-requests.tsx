"use client";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import { getDateRangeFromFilter, PRESET_DAYS } from "@/lib/dateRange";
import { useRequestBreakdown, useTenantInsightsStats } from "@/lib/hooks";
import { enumValidator, isoDateValidator, useUrlParams } from "@/lib/hooks/useUrlParams";
import type { RepoInsights } from "@/lib/types/dashboard";
import { formatDuration } from "@/lib/utils/format";
import { ArrowDown, ArrowUp, ArrowUpDown, BarChart3, Eye, Info, Search } from "lucide-react";
import React, { useEffect, useMemo, useState } from "react";
import InFlightPrsTab from "./in-flight-prs";

type StatsSortKey = "severity" | "repo" | "team" | "prCount" | "openCount" | "escalatedCount" | "breachedCount" | "p50" | "p90" | "p99";
type SortDir = "asc" | "desc";

// Rounds a funnel ratio to a whole percent, but never reports a misleading 0% or 100%: any
// non-zero numerator shows at least 1%, and anything short of the full denominator shows at most
// 99% — so e.g. 199 of 200 reads as 99%, not a rounded-up 100%. Returns null for an empty group.
function funnelPercent(numerator: number, denominator: number): number | null {
  if (denominator <= 0) return null;
  const rounded = Math.round((numerator / denominator) * 100);
  if (rounded >= 100 && numerator < denominator) return 99;
  if (rounded <= 0 && numerator > 0) return 1;
  return rounded;
}

function durationStyle(seconds: number): string {
  if (seconds < 14400) return "text-success bg-success/10";
  if (seconds < 86400) return "text-warning bg-warning/10";
  return "text-destructive bg-destructive/10";
}

function compareBySeverity(a: RepoInsights, b: RepoInsights): number {
  return a.breachedCount - b.breachedCount || a.escalatedCount - b.escalatedCount || a.prCount - b.prCount;
}

function compareByKey(a: RepoInsights, b: RepoInsights, key: StatsSortKey, dir: SortDir): number {
  let cmp = 0;
  switch (key) {
    case "severity":
      cmp = compareBySeverity(a, b);
      break;
    case "repo":
      cmp = a.repo.localeCompare(b.repo);
      break;
    case "team":
      cmp = a.owningTeam.localeCompare(b.owningTeam);
      break;
    case "prCount":
      cmp = a.prCount - b.prCount;
      break;
    case "openCount":
      cmp = a.openCount - b.openCount;
      break;
    case "escalatedCount":
      cmp = a.escalatedCount - b.escalatedCount;
      break;
    case "breachedCount":
      cmp = a.breachedCount - b.breachedCount;
      break;
    case "p50":
      cmp = a.p50Seconds - b.p50Seconds;
      break;
    case "p90":
      cmp = a.p90Seconds - b.p90Seconds;
      break;
    case "p99":
      cmp = a.p99Seconds - b.p99Seconds;
      break;
    default:
      key satisfies never;
  }
  return dir === "desc" ? -cmp : cmp;
}

type TabKey = "stats" | "inflight";

const tabs = [
  { key: "stats" as const, label: "PR Activity & SLA Health", icon: BarChart3 },
  { key: "inflight" as const, label: "In-Flight PRs", icon: Eye },
];

export default function TenantRequestsPage() {
  const [params, setParams] = useUrlParams(
    { tab: "stats", dateFilter: "lastMonth", dateFrom: "", dateTo: "" },
    {
      tab: enumValidator(["stats", "inflight"] as const, "stats"),
      dateFilter: enumValidator(["lastWeek", "last2Weeks", "lastMonth", "lastYear", "custom"] as const, "lastMonth"),
      dateFrom: isoDateValidator,
      dateTo: isoDateValidator,
    }
  );
  const activeTab = params.tab as TabKey;
  const dateFilter = params.dateFilter as "lastWeek" | "last2Weeks" | "lastMonth" | "lastYear" | "custom";

  const isDateRangeValid = !(dateFilter === "custom" && params.dateFrom && params.dateTo && params.dateFrom > params.dateTo);

  useEffect(() => {
    if (dateFilter === "custom" && params.dateFrom && params.dateTo && params.dateFrom > params.dateTo) {
      setParams({ dateFilter: "lastMonth", dateFrom: "", dateTo: "" });
    }
  }, [dateFilter, params.dateFrom, params.dateTo, setParams]);

  const dateRange = useMemo(
    () =>
      getDateRangeFromFilter({
        dateFilter,
        customDateRange: { start: params.dateFrom || undefined, end: params.dateTo || undefined },
        customValue: "custom",
        fallbackValue: "lastMonth",
        presetDays: {
          lastWeek: PRESET_DAYS.lastWeek,
          last2Weeks: PRESET_DAYS.last2Weeks,
          lastMonth: PRESET_DAYS.lastMonth,
          lastYear: PRESET_DAYS.lastYear,
        },
      }),
    [dateFilter, params.dateFrom, params.dateTo]
  );

  const [search, setSearch] = useState("");
  const [sortKey, setSortKey] = useState<StatsSortKey>("severity");
  const [sortDir, setSortDir] = useState<SortDir>("desc");
  const [page, setPage] = useState(0);
  const pageSize = 20;

  const {
    data: realRepos,
    isLoading: statsLoading,
    error: statsError,
  } = useTenantInsightsStats(
    isDateRangeValid ? dateRange.from : undefined,
    isDateRangeValid ? dateRange.to : undefined,
    isDateRangeValid && activeTab === "stats"
  );

  const { data: requestBreakdown, isLoading: breakdownLoading } = useRequestBreakdown(
    isDateRangeValid ? dateRange.from : undefined,
    isDateRangeValid ? dateRange.to : undefined,
    isDateRangeValid && activeTab === "stats"
  );

  // The funnel and the repo table load independently, so keep their loading/error state separate:
  // if the breakdown endpoint fails, the funnel cards degrade to a dash while the table still
  // renders (and vice-versa), rather than one failure blanking the whole tab.
  const repos = realRepos ?? [];

  // Request funnel: total support requests → % that are PRs → % of those PRs needing intervention.
  // All three counts share the ticket-creation date anchor, so they are nested subsets and the
  // percentages are coherent (PR share never exceeds 100%).
  const prPercentage = requestBreakdown ? funnelPercent(requestBreakdown.totalPrTickets, requestBreakdown.totalSupportTickets) : null;
  const interventionRate = requestBreakdown ? funnelPercent(requestBreakdown.interventionPrTickets, requestBreakdown.totalPrTickets) : null;

  const totals = useMemo(() => {
    if (repos.length === 0) {
      return { prCount: 0, openCount: 0, escalatedCount: 0, breachedCount: 0 };
    }
    return repos.reduce(
      (acc, r) => ({
        prCount: acc.prCount + r.prCount,
        openCount: acc.openCount + r.openCount,
        escalatedCount: acc.escalatedCount + r.escalatedCount,
        breachedCount: acc.breachedCount + r.breachedCount,
      }),
      { prCount: 0, openCount: 0, escalatedCount: 0, breachedCount: 0 }
    );
  }, [repos]);

  const filteredAndSorted = useMemo(() => {
    const q = search.toLowerCase();
    const filtered = q ? repos.filter((r) => r.repo.toLowerCase().includes(q) || r.owningTeam.toLowerCase().includes(q)) : repos;
    return [...filtered].sort((a, b) => compareByKey(a, b, sortKey, sortDir));
  }, [repos, search, sortKey, sortDir]);

  const totalPages = Math.ceil(filteredAndSorted.length / pageSize);
  const pagedRepos = filteredAndSorted.slice(page * pageSize, (page + 1) * pageSize);

  const handleSort = (key: StatsSortKey) => {
    setPage(0);
    if (sortKey === key) {
      setSortDir((d) => (d === "desc" ? "asc" : "desc"));
    } else {
      setSortKey(key);
      setSortDir(key === "repo" || key === "team" ? "asc" : "desc");
    }
  };

  const handleSearch = (value: string) => {
    setSearch(value);
    setPage(0);
  };

  const repoCount = repos.length;
  const noSlaRepoCount = repos.filter((r) => r.hasSla === false).length;
  useEffect(() => {
    const missingHasSla = repos.filter((r) => r.hasSla === undefined);
    if (missingHasSla.length > 0) {
      console.warn(
        `[tenant-requests] ${missingHasSla.length} repo(s) missing hasSla — likely API/UI version skew: ${missingHasSla.map((r) => r.repo).join(", ")}`
      );
    }
  }, [repos]);

  return (
    <TooltipProvider delayDuration={200}>
      <div className="space-y-6">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h1 className="text-foreground text-2xl font-bold">Tenant Requests</h1>
            <p className="text-muted-foreground text-sm">PR tracking, review lifecycle, and SLA health</p>
          </div>
          {activeTab === "stats" && (
            <div className="flex flex-wrap items-center gap-2">
              <Select
                value={dateFilter}
                onValueChange={(v) => {
                  if (v !== "custom") {
                    setParams({ dateFilter: v, dateFrom: "", dateTo: "" });
                  } else {
                    setParams({ dateFilter: "custom" });
                  }
                  setPage(0);
                }}
              >
                <SelectTrigger className="w-[160px]">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="lastWeek">Last Week</SelectItem>
                  <SelectItem value="last2Weeks">Last 2 Weeks</SelectItem>
                  <SelectItem value="lastMonth">Last Month</SelectItem>
                  <SelectItem value="lastYear">Last Year</SelectItem>
                  <SelectItem value="custom">Custom</SelectItem>
                </SelectContent>
              </Select>
              {dateFilter === "custom" && (
                <>
                  <Input
                    type="date"
                    aria-label="Date filter start"
                    value={params.dateFrom}
                    onChange={(e) => {
                      setParams({ dateFrom: e.target.value });
                      setPage(0);
                    }}
                    className="w-[150px]"
                  />
                  <Input
                    type="date"
                    aria-label="Date filter end"
                    value={params.dateTo}
                    onChange={(e) => {
                      setParams({ dateTo: e.target.value });
                      setPage(0);
                    }}
                    className="w-[150px]"
                  />
                </>
              )}
              {dateFilter === "custom" && !isDateRangeValid && <span className="text-destructive text-xs font-medium">Invalid range</span>}
            </div>
          )}
        </div>

        <Tabs value={activeTab} onValueChange={(v) => setParams({ tab: v })} className="space-y-4">
          <TabsList>
            {tabs.map((tab) => {
              const Icon = tab.icon;
              return (
                <TabsTrigger key={tab.key} value={tab.key} className="cursor-pointer">
                  <Icon className="h-4 w-4" />
                  {tab.label}
                </TabsTrigger>
              );
            })}
          </TabsList>

          <TabsContent value="stats" className="space-y-6">
            <div>
              <h2 className="text-foreground text-base font-semibold">PR Review Support Automation</h2>
              <p className="text-muted-foreground text-sm">
                Share of incoming requests handled automatically as PR reviews, and how often those still needed a human
              </p>
            </div>

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
              <StatCard
                label="Total Requests"
                value={requestBreakdown?.totalSupportTickets ?? null}
                isLoading={breakdownLoading}
                accent="primary"
                tooltip="Total support requests created in the selected period"
              />
              <StatCard
                label="Handled by Bot"
                value={prPercentage}
                suffix="%"
                isLoading={breakdownLoading}
                accent="info"
                tooltip={
                  requestBreakdown
                    ? `${requestBreakdown.totalPrTickets} of ${requestBreakdown.totalSupportTickets} requests were PR reviews tracked automatically by the bot`
                    : "Share of incoming requests the bot handled automatically (PR reviews)"
                }
              />
              <StatCard
                label="Needed Manual Escalation"
                value={interventionRate}
                suffix="%"
                isLoading={breakdownLoading}
                valueClass={interventionRate !== null && interventionRate > 0 ? "text-warning" : undefined}
                accent="indigo"
                tooltip={
                  requestBreakdown
                    ? `${requestBreakdown.interventionPrTickets} of ${requestBreakdown.totalPrTickets} bot-handled requests required a manual escalation`
                    : "Share of bot-handled requests that required a manual escalation"
                }
              />
            </div>

            <div>
              <h2 className="text-foreground text-base font-semibold">PR Activity & SLA Health</h2>
              <p className="text-muted-foreground text-sm">Pull request tracking across repositories</p>
            </div>

            <div className="grid grid-cols-2 gap-4 lg:grid-cols-6">
              <StatCard label="Repositories" value={repoCount} isLoading={statsLoading} accent="primary" />
              <StatCard
                label="No SLA Repos"
                value={noSlaRepoCount}
                isLoading={statsLoading}
                valueClass={noSlaRepoCount > 0 ? "text-warning" : undefined}
                accent="warning"
              />
              <StatCard label="Total PRs" value={totals.prCount} isLoading={statsLoading} accent="info" />
              <StatCard label="Open" value={totals.openCount} isLoading={statsLoading} accent="success" />
              <StatCard
                label="Escalated"
                value={totals.escalatedCount}
                isLoading={statsLoading}
                valueClass={totals.escalatedCount > 0 ? "text-warning" : undefined}
                accent="purple"
              />
              <StatCard
                label="SLA Breached"
                value={totals.breachedCount}
                isLoading={statsLoading}
                valueClass={totals.breachedCount > 0 ? "text-destructive" : undefined}
                accent="destructive"
              />
            </div>

            <div className="bg-card rounded-xl border">
              <div className="flex items-center justify-between border-b px-6 py-4">
                <div>
                  <h2 className="text-foreground text-base font-semibold">Repositories</h2>
                  {filteredAndSorted.length > 0 && (
                    <p className="text-muted-foreground mt-0.5 text-xs">
                      {filteredAndSorted.length === repoCount ? `${repoCount} repos` : `${filteredAndSorted.length} of ${repoCount} repos`}
                    </p>
                  )}
                </div>
                <div className="relative">
                  <Search className="text-muted-foreground absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2" />
                  <Input
                    type="text"
                    value={search}
                    onChange={(e) => handleSearch(e.target.value)}
                    placeholder="Filter repos or teams..."
                    className="w-64 pl-9"
                  />
                </div>
              </div>

              {statsError ? (
                <div className="text-destructive p-16 text-center text-sm">Failed to load data — please try again</div>
              ) : statsLoading ? (
                <div className="text-muted-foreground p-16 text-center text-sm">Loading...</div>
              ) : filteredAndSorted.length === 0 ? (
                <div className="text-muted-foreground p-16 text-center text-sm">
                  {search ? "No repos match your search" : "No PR data for this period"}
                </div>
              ) : (
                <>
                  <div className="overflow-x-auto">
                    <table className="min-w-full divide-y">
                      <thead className="bg-muted">
                        <tr>
                          <SortHeader
                            label="Repository"
                            sortKey="repo"
                            align="left"
                            activeSortKey={sortKey}
                            sortDir={sortDir}
                            onSort={handleSort}
                          />
                          <SortHeader
                            label="Team"
                            sortKey="team"
                            align="left"
                            activeSortKey={sortKey}
                            sortDir={sortDir}
                            onSort={handleSort}
                          />
                          <SortHeader
                            label="PRs"
                            sortKey="prCount"
                            align="right"
                            activeSortKey={sortKey}
                            sortDir={sortDir}
                            onSort={handleSort}
                          />
                          <SortHeader
                            label="Open"
                            sortKey="openCount"
                            align="right"
                            activeSortKey={sortKey}
                            sortDir={sortDir}
                            onSort={handleSort}
                          />
                          <SortHeader
                            label="Escalated"
                            sortKey="escalatedCount"
                            align="right"
                            activeSortKey={sortKey}
                            sortDir={sortDir}
                            onSort={handleSort}
                          />
                          <SortHeader
                            label="Breached"
                            sortKey="breachedCount"
                            align="right"
                            activeSortKey={sortKey}
                            sortDir={sortDir}
                            onSort={handleSort}
                          />
                          <SortHeader
                            label="p50"
                            sortKey="p50"
                            align="right"
                            activeSortKey={sortKey}
                            sortDir={sortDir}
                            onSort={handleSort}
                            tooltip="50% of PRs are resolved within this time"
                          />
                          <SortHeader
                            label="p90"
                            sortKey="p90"
                            align="right"
                            activeSortKey={sortKey}
                            sortDir={sortDir}
                            onSort={handleSort}
                            tooltip="90% of PRs are resolved within this time"
                          />
                          <SortHeader
                            label="p99"
                            sortKey="p99"
                            align="right"
                            activeSortKey={sortKey}
                            sortDir={sortDir}
                            onSort={handleSort}
                            tooltip="99% of PRs are resolved within this time"
                          />
                        </tr>
                      </thead>
                      <tbody className="divide-y">
                        {pagedRepos.map((repo) => (
                          <tr key={repo.repo} className="hover:bg-accent transition-colors">
                            <td className="px-4 py-2 text-sm">
                              <div className="flex items-center gap-2">
                                <span className="text-foreground font-medium">{repo.repo}</span>
                                {repo.hasSla === false && (
                                  <Badge variant="outline" className="text-warning border-warning/40">
                                    No SLA
                                  </Badge>
                                )}
                              </div>
                            </td>
                            <td className="text-muted-foreground px-4 py-2 text-sm">{repo.owningTeam}</td>
                            <td className="text-foreground px-4 py-2 text-right font-mono text-sm tabular-nums">{repo.prCount}</td>
                            <td className="text-muted-foreground px-4 py-2 text-right font-mono text-sm tabular-nums">{repo.openCount}</td>
                            <td className="px-4 py-2 text-right">
                              <CountBadge value={repo.escalatedCount} accent="warning" />
                            </td>
                            <td className="px-4 py-2 text-right">
                              {repo.hasSla === false ? (
                                <span className="text-muted-foreground/50 tabular-nums">{"—"}</span>
                              ) : (
                                <CountBadge value={repo.breachedCount} accent="destructive" />
                              )}
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
                    <div className="flex items-center justify-end gap-4 border-t px-6 py-3">
                      <span className="text-muted-foreground text-sm">
                        Page {page + 1} of {totalPages}
                      </span>
                      <Button variant="outline" size="sm" onClick={() => setPage((p) => p - 1)} disabled={page === 0}>
                        Previous
                      </Button>
                      <Button variant="outline" size="sm" onClick={() => setPage((p) => p + 1)} disabled={page >= totalPages - 1}>
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
  );
}

function StatCard({
  label,
  value,
  suffix,
  isLoading,
  valueClass,
  accent = "primary",
  tooltip,
}: {
  label: string;
  value: number | null;
  suffix?: string;
  isLoading: boolean;
  valueClass?: string;
  accent?: "primary" | "warning" | "destructive" | "success" | "info" | "purple" | "indigo";
  tooltip?: string;
}) {
  const accentBg: Record<NonNullable<typeof accent>, string> = {
    primary: "bg-primary/15",
    warning: "bg-warning/15",
    destructive: "bg-destructive/15",
    success: "bg-success/15",
    info: "bg-info/15",
    purple: "bg-chart-4/15",
    indigo: "bg-chart-9/15",
  };
  const circleClass = accentBg[accent];

  const card = (
    <div className="bg-card relative overflow-hidden rounded-xl border p-6">
      <div className={`absolute -top-4 -right-4 h-24 w-24 rounded-full ${circleClass}`} />
      <div className={`absolute -right-6 -bottom-6 h-20 w-20 rounded-full ${circleClass}`} />
      <div className="relative">
        <div className="mb-2 flex items-center gap-2">
          <p className="text-muted-foreground text-sm font-medium">{label}</p>
          {tooltip && <Info className="text-muted-foreground/70 h-3.5 w-3.5" />}
        </div>
        {isLoading ? (
          <div className="bg-muted h-9 w-16 animate-pulse rounded" />
        ) : (
          <p className={`font-mono text-3xl font-semibold tracking-tight tabular-nums ${valueClass ?? "text-foreground"}`}>
            {value !== null ? `${value}${suffix ?? ""}` : "—"}
          </p>
        )}
      </div>
    </div>
  );

  if (!tooltip) return card;

  return (
    <Tooltip>
      <TooltipTrigger asChild>{card}</TooltipTrigger>
      <TooltipContent side="bottom" sideOffset={6} className="max-w-[220px]">
        {tooltip}
      </TooltipContent>
    </Tooltip>
  );
}

function SortHeader({
  label,
  sortKey,
  activeSortKey,
  sortDir,
  onSort,
  align,
  tooltip,
}: {
  label: string;
  sortKey: StatsSortKey;
  activeSortKey: StatsSortKey;
  sortDir: SortDir;
  onSort: (key: StatsSortKey) => void;
  align: "left" | "right";
  tooltip?: string;
}) {
  const isActive = activeSortKey === sortKey;
  const textAlign = align === "left" ? "text-left" : "text-right";

  return (
    <th
      className={`px-4 py-2 ${textAlign} text-foreground hover:bg-muted/70 cursor-pointer text-xs font-bold uppercase transition-colors select-none`}
      onClick={() => onSort(sortKey)}
    >
      <span className={`inline-flex items-center gap-1 ${align === "right" ? "w-full justify-end" : ""}`}>
        {label}
        {isActive ? (
          sortDir === "asc" ? (
            <ArrowUp className="h-3.5 w-3.5" />
          ) : (
            <ArrowDown className="h-3.5 w-3.5" />
          )
        ) : (
          <ArrowUpDown className="text-muted-foreground h-3.5 w-3.5" />
        )}
        {tooltip && (
          <Tooltip>
            <TooltipTrigger asChild onClick={(e: React.MouseEvent) => e.stopPropagation()}>
              <Info className="text-muted-foreground hover:text-foreground h-3 w-3 transition-colors" />
            </TooltipTrigger>
            <TooltipContent side="bottom" sideOffset={6} className="max-w-[200px]">
              {tooltip}
            </TooltipContent>
          </Tooltip>
        )}
      </span>
    </th>
  );
}

function CountBadge({ value, accent }: { value: number; accent: "warning" | "destructive" }) {
  if (value === 0) return <span className="text-muted-foreground/50 font-mono text-sm tabular-nums">0</span>;

  const style = accent === "destructive" ? "bg-destructive/10 text-destructive" : "bg-warning/10 text-warning";

  return <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-semibold tabular-nums ${style}`}>{value}</span>;
}

function DurationPill({ seconds }: { seconds: number }) {
  return (
    <span className={`inline-block rounded-md px-2 py-0.5 text-xs font-medium tabular-nums ${durationStyle(seconds)}`}>
      {formatDuration(seconds)}
    </span>
  );
}
