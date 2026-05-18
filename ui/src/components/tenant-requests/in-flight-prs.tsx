"use client";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import { useInFlightPrs } from "@/lib/hooks";
import type { InFlightPr } from "@/lib/types/dashboard";
import { ArrowDown, ArrowUp, ArrowUpDown, ExternalLink, HelpCircle, Search } from "lucide-react";
import { useEffect, useMemo, useState } from "react";

type SortKey = "severity" | "pr" | "status" | "waitingOn" | "sla" | "age" | "lastReview" | "team";
type SortDir = "asc" | "desc";

const STATUS_SEVERITY: Record<string, number> = {
  ESCALATED: 0,
  OPEN: 1,
  CHANGES_REQUESTED: 2,
  APPROVED: 3,
};

function statusSeverity(status: string): number {
  return STATUS_SEVERITY[status] ?? 99;
}

function statusBadgeStyle(status: string): string {
  switch (status) {
    case "OPEN":
      return "bg-info/10 text-info";
    case "CHANGES_REQUESTED":
      return "bg-warning/10 text-warning";
    case "APPROVED":
      return "bg-success/10 text-success";
    case "ESCALATED":
      return "bg-destructive/10 text-destructive";
    default:
      return "bg-muted text-foreground";
  }
}

function statusLabel(status: string): string {
  switch (status) {
    case "CHANGES_REQUESTED":
      return "Changes Requested";
    default:
      return status.charAt(0) + status.slice(1).toLowerCase();
  }
}

function slackDeepLink(channelId: string, queryTs: string): string {
  return `https://slack.com/archives/${channelId}/p${queryTs.replace(".", "")}`;
}

function ageFromNow(isoDate: string): { totalSeconds: number; label: string } {
  const diff = (Date.now() - new Date(isoDate).getTime()) / 1000;
  const days = Math.floor(diff / 86400);
  const hours = Math.floor((diff % 86400) / 3600);
  return { totalSeconds: diff, label: days > 0 ? `${days}d ${hours}h` : `${hours}h` };
}

function relativeTime(isoDate: string | null): string {
  if (!isoDate) return "—";
  const diff = (Date.now() - new Date(isoDate).getTime()) / 1000;
  const days = Math.floor(diff / 86400);
  const hours = Math.floor((diff % 86400) / 3600);
  if (days > 0) return `${days}d ${hours}h ago`;
  if (hours > 0) return `${hours}h ago`;
  const mins = Math.floor(diff / 60);
  return `${mins}m ago`;
}

interface SlaInfo {
  label: string;
  style: string;
  sortValue: number;
}

type SlaState =
  | { kind: "none" }
  | { kind: "unknown" }
  | { kind: "paused"; remainingSeconds: number }
  | { kind: "active"; deadlineMs: number; remainingSec: number }
  | { kind: "breached"; deadlineMs: number; remainingSec: number }
  | { kind: "missing" };

function classifySla(pr: InFlightPr, now: number): SlaState {
  if (pr.hasSla === false) {
    return { kind: "none" };
  }
  const unknownGate: "unknown" | "active" | "paused" | "missing" | "breached" = pr.hasSla === undefined ? "unknown" : "active";
  if (pr.slaRemainingSeconds != null && pr.slaDeadline == null) {
    return { kind: "paused", remainingSeconds: pr.slaRemainingSeconds };
  }
  if (pr.slaDeadline != null) {
    const deadlineMs = new Date(pr.slaDeadline).getTime();
    const remainingSec = (deadlineMs - now) / 1000;
    return remainingSec > 0 ? { kind: "active", deadlineMs, remainingSec } : { kind: "breached", deadlineMs, remainingSec };
  }
  void unknownGate;
  return { kind: "missing" };
}

function slaInfo(pr: InFlightPr, now: number): SlaInfo {
  const state = classifySla(pr, now);
  switch (state.kind) {
    case "none":
      return { label: "No SLA", style: "bg-warning/10 text-warning", sortValue: 9999 };
    case "paused": {
      const hours = Math.max(0, state.remainingSeconds / 3600);
      return {
        label: `Paused (${hours.toFixed(1)}h remaining)`,
        style: "text-muted-foreground",
        sortValue: 1000 + state.remainingSeconds,
      };
    }
    case "active": {
      const hours = state.remainingSec / 3600;
      const style = hours > 4 ? "text-success bg-success/10" : "text-warning bg-warning/10";
      return { label: `${hours.toFixed(1)}h left`, style, sortValue: state.remainingSec };
    }
    case "breached": {
      const daysAgo = Math.floor(Math.abs(state.remainingSec) / 86400);
      return {
        label: `Breached ${daysAgo > 0 ? `${daysAgo}d ago` : "today"}`,
        style: "text-destructive bg-destructive/10",
        sortValue: state.remainingSec,
      };
    }
    case "unknown":
    case "missing":
      return {
        label: "SLA data missing",
        style: "text-muted-foreground bg-muted",
        sortValue: Number.MAX_SAFE_INTEGER,
      };
  }
}

function compareByKey(a: InFlightPr, b: InFlightPr, key: SortKey, dir: SortDir, now: number): number {
  let cmp = 0;
  switch (key) {
    case "severity":
      cmp = statusSeverity(a.status) - statusSeverity(b.status);
      break;
    case "pr":
      cmp = a.githubRepo.localeCompare(b.githubRepo) || a.prNumber - b.prNumber;
      break;
    case "status":
      cmp = statusSeverity(a.status) - statusSeverity(b.status);
      break;
    case "waitingOn":
      cmp = a.waitingOn.localeCompare(b.waitingOn);
      break;
    case "sla":
      cmp = slaInfo(a, now).sortValue - slaInfo(b, now).sortValue;
      break;
    case "age": {
      const ageA = new Date(a.prCreatedAt).getTime();
      const ageB = new Date(b.prCreatedAt).getTime();
      cmp = ageA - ageB;
      break;
    }
    case "lastReview": {
      const la = a.lastReviewAt ? new Date(a.lastReviewAt).getTime() : 0;
      const lb = b.lastReviewAt ? new Date(b.lastReviewAt).getTime() : 0;
      cmp = la - lb;
      break;
    }
    case "team":
      cmp = a.owningTeamLabel.localeCompare(b.owningTeamLabel);
      break;
    default:
      key satisfies never;
  }
  return dir === "desc" ? -cmp : cmp;
}

function repoShortName(fullRepo: string): string {
  const parts = fullRepo.split("/");
  return parts.length > 1 ? parts[parts.length - 1] : fullRepo;
}

export default function InFlightPrsTab() {
  const [teamFilter, setTeamFilter] = useState<string>("");
  const { data: allPrs, isLoading, error } = useInFlightPrs();
  const unfilteredPrs = useMemo(() => allPrs ?? [], [allPrs]);

  const teams = useMemo(() => {
    const set = new Map<string, string>();
    unfilteredPrs.forEach((pr) => set.set(pr.owningTeam, pr.owningTeamLabel));
    return Array.from(set.entries()).sort((a, b) => a[1].localeCompare(b[1]));
  }, [unfilteredPrs]);

  const prs = useMemo(
    () => (teamFilter ? unfilteredPrs.filter((pr) => pr.owningTeam === teamFilter) : unfilteredPrs),
    [unfilteredPrs, teamFilter]
  );

  const [search, setSearch] = useState("");
  const [sortKey, setSortKey] = useState<SortKey>("severity");
  const [sortDir, setSortDir] = useState<SortDir>("desc");
  const [page, setPage] = useState(0);
  const pageSize = 20;

  const [clockTick, setClockTick] = useState(0);
  useEffect(() => {
    const id = setInterval(() => setClockTick((t) => t + 1), 60_000);
    return () => clearInterval(id);
  }, []);
  // eslint-disable-next-line react-hooks/purity
  const dataTimestamp = useMemo(() => Date.now(), [prs, clockTick]);

  useEffect(() => {
    const classified = prs.map((p) => ({ pr: p, state: classifySla(p, dataTimestamp) }));
    const missingHasSla = classified.filter((c) => c.pr.hasSla === undefined).map((c) => `${c.pr.githubRepo}#${c.pr.prNumber}`);
    if (missingHasSla.length > 0) {
      console.warn(
        `[in-flight-prs] ${missingHasSla.length} PR(s) missing hasSla — likely API/UI version skew: ${missingHasSla.join(", ")}`
      );
    }
    const brokenSla = classified.filter((c) => c.state.kind === "missing").map((c) => `${c.pr.githubRepo}#${c.pr.prNumber}`);
    if (brokenSla.length > 0) {
      console.error(`[in-flight-prs] ${brokenSla.length} PR(s) have hasSla!=false but both SLA fields are null: ${brokenSla.join(", ")}`);
    }
  }, [prs, dataTimestamp]);

  const totals = useMemo(() => {
    let waitingOnTeam = 0;
    let waitingOnTenant = 0;
    let waitingOnMerge = 0;
    let breached = 0;
    let noSla = 0;
    for (const pr of prs) {
      if (pr.waitingOn === "TEAM") waitingOnTeam++;
      else if (pr.waitingOn === "TENANT") waitingOnTenant++;
      else if (pr.waitingOn === "MERGE") waitingOnMerge++;
      const state = classifySla(pr, dataTimestamp);
      if (state.kind === "breached") breached++;
      if (state.kind === "none") noSla++;
    }
    return { total: prs.length, waitingOnTeam, waitingOnTenant, waitingOnMerge, breached, noSla };
  }, [prs, dataTimestamp]);

  const filteredAndSorted = useMemo(() => {
    const q = search.toLowerCase();
    const filtered = q
      ? prs.filter(
          (pr) =>
            pr.githubRepo.toLowerCase().includes(q) ||
            pr.owningTeamLabel.toLowerCase().includes(q) ||
            pr.status.toLowerCase().includes(q) ||
            String(pr.prNumber).includes(q)
        )
      : prs;
    return [...filtered].sort((a, b) => compareByKey(a, b, sortKey, sortDir, dataTimestamp));
  }, [prs, search, sortKey, sortDir, dataTimestamp]);

  const totalPages = Math.ceil(filteredAndSorted.length / pageSize);
  const pagedPrs = filteredAndSorted.slice(page * pageSize, (page + 1) * pageSize);

  const handleSort = (key: SortKey) => {
    setPage(0);
    if (sortKey === key) {
      setSortDir((d) => (d === "desc" ? "asc" : "desc"));
    } else {
      setSortKey(key);
      setSortDir(key === "pr" || key === "team" || key === "waitingOn" ? "asc" : "desc");
    }
  };

  const handleSearch = (value: string) => {
    setSearch(value);
    setPage(0);
  };

  const ALL_TEAMS = "__all";

  return (
    <TooltipProvider delayDuration={200}>
      <div className="space-y-6">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 className="text-foreground text-base font-semibold">In-Flight PRs</h2>
            <p className="text-muted-foreground text-sm">Currently open pull requests across tracked repositories</p>
          </div>
          <Select
            value={teamFilter || ALL_TEAMS}
            onValueChange={(v) => {
              setTeamFilter(v === ALL_TEAMS ? "" : v);
              setPage(0);
            }}
          >
            <SelectTrigger className="w-[200px]">
              <SelectValue placeholder="All Teams" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL_TEAMS}>All Teams</SelectItem>
              {teams.map(([code, label]) => (
                <SelectItem key={code} value={code}>
                  {label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="grid grid-cols-2 gap-4 lg:grid-cols-6">
          <StatCard label="Total In-Flight" value={totals.total} isLoading={isLoading} accent="primary" />
          <StatCard label="Waiting on Team" value={totals.waitingOnTeam} isLoading={isLoading} accent="info" />
          <StatCard label="Waiting on Tenant" value={totals.waitingOnTenant} isLoading={isLoading} accent="indigo" />
          <StatCard
            label="Waiting on Merge"
            value={totals.waitingOnMerge}
            isLoading={isLoading}
            valueClass={totals.waitingOnMerge > 0 ? "text-success" : undefined}
            accent="success"
          />
          <StatCard
            label="No SLA"
            value={totals.noSla}
            isLoading={isLoading}
            valueClass={totals.noSla > 0 ? "text-warning" : undefined}
            accent="warning"
          />
          <StatCard
            label="SLA Breached"
            value={totals.breached}
            isLoading={isLoading}
            valueClass={totals.breached > 0 ? "text-destructive" : undefined}
            accent="destructive"
          />
        </div>

        <div className="bg-card rounded-xl border">
          <div className="flex items-center justify-between border-b px-6 py-4">
            <div>
              <h2 className="text-foreground text-base font-semibold">Pull Requests</h2>
              {filteredAndSorted.length > 0 && (
                <p className="text-muted-foreground mt-0.5 text-xs">
                  {filteredAndSorted.length === prs.length ? `${prs.length} PRs` : `${filteredAndSorted.length} of ${prs.length} PRs`}
                </p>
              )}
            </div>
            <div className="relative">
              <Search className="text-muted-foreground absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2" />
              <Input
                type="text"
                value={search}
                onChange={(e) => handleSearch(e.target.value)}
                placeholder="Filter PRs, repos or teams..."
                className="w-64 pl-9"
              />
            </div>
          </div>

          {error ? (
            <div className="text-destructive p-16 text-center text-sm">Failed to load data — please try again</div>
          ) : isLoading ? (
            <div className="text-muted-foreground p-16 text-center text-sm">Loading...</div>
          ) : filteredAndSorted.length === 0 ? (
            <div className="text-muted-foreground p-16 text-center text-sm">{search ? "No PRs match your search" : "No in-flight PRs"}</div>
          ) : (
            <>
              <div className="overflow-x-auto">
                <table className="min-w-full divide-y whitespace-nowrap">
                  <thead className="bg-muted">
                    <tr>
                      <SortHeader label="PR" sortKey="pr" align="left" activeSortKey={sortKey} sortDir={sortDir} onSort={handleSort} />
                      <SortHeader
                        label="Status"
                        sortKey="status"
                        align="left"
                        activeSortKey={sortKey}
                        sortDir={sortDir}
                        onSort={handleSort}
                      />
                      <SortHeader
                        label="Waiting On"
                        sortKey="waitingOn"
                        align="left"
                        activeSortKey={sortKey}
                        sortDir={sortDir}
                        onSort={handleSort}
                      />
                      <SortHeader label="SLA" sortKey="sla" align="right" activeSortKey={sortKey} sortDir={sortDir} onSort={handleSort} />
                      <SortHeader label="Age" sortKey="age" align="right" activeSortKey={sortKey} sortDir={sortDir} onSort={handleSort} />
                      <SortHeader
                        label="Last Review"
                        sortKey="lastReview"
                        align="right"
                        activeSortKey={sortKey}
                        sortDir={sortDir}
                        onSort={handleSort}
                      />
                      <SortHeader label="Team" sortKey="team" align="left" activeSortKey={sortKey} sortDir={sortDir} onSort={handleSort} />
                      <th className="text-foreground px-4 py-2 text-right text-xs font-bold uppercase">Slack</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y">
                    {pagedPrs.map((pr) => {
                      const age = ageFromNow(pr.prCreatedAt);
                      const sla = slaInfo(pr, dataTimestamp);
                      return (
                        <tr key={`${pr.githubRepo}-${pr.prNumber}`} className="hover:bg-accent transition-colors">
                          <td className="px-4 py-2 text-sm">
                            <a
                              href={pr.prUrl}
                              target="_blank"
                              rel="noopener noreferrer"
                              className="text-foreground inline-flex items-center gap-1.5 font-medium hover:underline"
                            >
                              {repoShortName(pr.githubRepo)}#{pr.prNumber}
                              <ExternalLink className="h-3 w-3" />
                            </a>
                          </td>
                          <td className="px-4 py-2">
                            <span
                              className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-semibold ${statusBadgeStyle(pr.status)}`}
                            >
                              {statusLabel(pr.status)}
                            </span>
                          </td>
                          <td className="text-muted-foreground px-4 py-2 text-sm">{pr.waitingOn}</td>
                          <td className="px-4 py-2 text-right">
                            <span
                              className={`inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-xs font-medium tabular-nums ${sla.style}`}
                            >
                              {sla.label}
                              {sla.label.startsWith("Paused") && (
                                <Tooltip>
                                  <TooltipTrigger asChild>
                                    <HelpCircle className="text-muted-foreground h-3 w-3 cursor-help" />
                                  </TooltipTrigger>
                                  <TooltipContent sideOffset={5} className="max-w-[220px]">
                                    SLA clock is paused because the owning team has reviewed. It will resume when the tenant pushes updates.
                                  </TooltipContent>
                                </Tooltip>
                              )}
                            </span>
                          </td>
                          <td className="text-muted-foreground px-4 py-2 text-right font-mono text-sm tabular-nums">{age.label}</td>
                          <td className="text-muted-foreground px-4 py-2 text-right text-sm">{relativeTime(pr.lastReviewAt)}</td>
                          <td className="text-muted-foreground px-4 py-2 text-sm">{pr.owningTeamLabel}</td>
                          <td className="px-4 py-2 text-right">
                            {pr.ticketChannelId && pr.ticketQueryTs ? (
                              <a
                                href={slackDeepLink(pr.ticketChannelId, pr.ticketQueryTs)}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="text-foreground inline-flex items-center gap-1 text-xs hover:underline"
                              >
                                Thread
                                <ExternalLink className="h-3 w-3" />
                              </a>
                            ) : (
                              <span className="text-muted-foreground/50">{"—"}</span>
                            )}
                          </td>
                        </tr>
                      );
                    })}
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
      </div>
    </TooltipProvider>
  );
}

function StatCard({
  label,
  value,
  isLoading,
  valueClass,
  accent = "primary",
}: {
  label: string;
  value: number;
  isLoading: boolean;
  valueClass?: string;
  accent?: "primary" | "warning" | "destructive" | "success" | "info" | "purple" | "indigo";
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

  return (
    <div className="bg-card relative overflow-hidden rounded-xl border p-6">
      <div className={`absolute -top-4 -right-4 h-24 w-24 rounded-full ${circleClass}`} />
      <div className={`absolute -right-6 -bottom-6 h-20 w-20 rounded-full ${circleClass}`} />
      <div className="relative">
        <p className="text-muted-foreground mb-2 text-sm font-medium">{label}</p>
        {isLoading ? (
          <div className="bg-muted h-9 w-16 animate-pulse rounded" />
        ) : (
          <p className={`font-mono text-3xl font-semibold tracking-tight tabular-nums ${valueClass ?? "text-foreground"}`}>{value}</p>
        )}
      </div>
    </div>
  );
}

function SortHeader({
  label,
  sortKey,
  activeSortKey,
  sortDir,
  onSort,
  align,
}: {
  label: string;
  sortKey: SortKey;
  activeSortKey: SortKey;
  sortDir: SortDir;
  onSort: (key: SortKey) => void;
  align: "left" | "right";
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
      </span>
    </th>
  );
}
