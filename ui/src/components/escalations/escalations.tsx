"use client";

import LoadingSkeleton from "@/components/LoadingSkeleton";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { SingleSelectFilter } from "@/components/ui/single-select-filter";
import { useTeamFilter } from "@/contexts/TeamFilterContext";
import { useAuth } from "@/hooks/useAuth";
import { useNow } from "@/hooks/useNow";
import { TEAM_SCOPE } from "@/lib/constants";
import { getDateRangeFromFilter, PRESET_DAYS } from "@/lib/dateRange";
import { useEscalations, useRegistry, useTenantTeams } from "@/lib/hooks";
import { enumValidator, isoDateValidator, nonNegativeIntValidator, useUrlParams } from "@/lib/hooks/useUrlParams";
import { normalizeTeamKey } from "@/lib/teamUtils";
import { ArrowDown, ArrowUp, ArrowUpDown } from "lucide-react";
import { useEffect, useMemo, useRef } from "react";
import EscalatedToMyTeamTable from "./EscalatedToMyTeamTable";

type SortColumn = "ticketId" | "escalatingTeam" | "escalatedTo" | "openedAt" | "resolvedAt" | "duration";

function SortableHeader({
  col,
  label,
  sortColumn,
  sortDirection,
  onSort,
}: {
  col: SortColumn;
  label: string;
  sortColumn: SortColumn;
  sortDirection: "asc" | "desc";
  onSort: (column: SortColumn) => void;
}) {
  return (
    <th
      className="text-foreground hover:bg-muted cursor-pointer px-4 py-2 text-left text-xs font-bold uppercase transition-colors select-none"
      onClick={() => onSort(col)}
    >
      <span className="inline-flex items-center gap-1">
        {label}
        {sortColumn === col ? (
          sortDirection === "asc" ? (
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

export default function EscalationsPage() {
  const { data: escalationsData, isLoading: isLoadingEscalations, error: errorEscalations } = useEscalations();
  const { data: tenantTeamsData } = useTenantTeams();
  const { data: registryData } = useRegistry();
  const ALL_TEAMS_FILTER = TEAM_SCOPE.ALL_TEAMS;
  type EscalationDateFilter = "" | "lastWeek" | "last2Weeks" | "lastMonth" | "custom";

  // Persist all filter / sort / page controls in the URL.
  // Validators guard against invalid URL values and auto-correct the URL.
  const [params, setParams] = useUrlParams(
    {
      dateFilter: "lastWeek",
      dateFrom: "",
      dateTo: "",
      status: "all",
      selectedTeam: "",
      impact: "all",
      tag: "",
      sortBy: "openedAt",
      sortDir: "desc",
      page: "0",
    },
    {
      dateFilter: enumValidator(["", "lastWeek", "last2Weeks", "lastMonth", "custom"] as const, "lastWeek"),
      dateFrom: isoDateValidator,
      dateTo: isoDateValidator,
      status: enumValidator(["all", "ongoing", "resolved"] as const, "all"),
      sortBy: enumValidator(["ticketId", "escalatingTeam", "escalatedTo", "openedAt", "resolvedAt", "duration"] as const, "openedAt"),
      sortDir: enumValidator(["asc", "desc"] as const, "desc"),
      page: nonNegativeIntValidator,
    }
  );

  // Casts are safe for status, dateFilter, sortBy, sortDir, and page —
  // each has an enumValidator or nonNegativeIntValidator in the useUrlParams call above.
  // selectedTeam, impact, and tag have no validators and carry raw URL strings.
  const selectedTeam = params.selectedTeam;
  const statusFilter = params.status as "all" | "ongoing" | "resolved";
  const impactFilter = params.impact;
  const tagFilter = params.tag;
  const dateFilter = params.dateFilter as EscalationDateFilter;
  const sortColumn = params.sortBy as SortColumn;
  const sortDirection = params.sortDir as "asc" | "desc";
  const pageIndex = parseInt(params.page, 10);
  const pageSize = 15;

  // Correct the URL when custom date range is in an invalid order (dateFrom > dateTo).
  useEffect(() => {
    if (params.dateFilter === "custom" && params.dateFrom && params.dateTo && params.dateFrom > params.dateTo) {
      setParams({ dateFilter: "lastWeek", dateFrom: "", dateTo: "" });
    }
  }, [params.dateFilter, params.dateFrom, params.dateTo, setParams]);

  const {
    hasFullAccess,
    effectiveTeams,
    hasNoTeamScope: contextHasNoTeamScope,
    isViewingAsEscalationTeam: contextIsViewingAsEscalationTeam,
    selectedTeam: teamFilterSelectedTeam,
  } = useTeamFilter();
  const { actualEscalationTeams } = useAuth();
  const hasNoTeamScope = contextHasNoTeamScope ?? effectiveTeams.includes(TEAM_SCOPE.NO_TEAMS);
  const isViewingAsEscalationTeam =
    contextIsViewingAsEscalationTeam ?? (!!teamFilterSelectedTeam && actualEscalationTeams.includes(teamFilterSelectedTeam));
  const now = useNow();

  // Reset the page-level team filter when the sidebar "View as" scope changes.
  // The ref starts as `undefined` (sentinel for "not yet seen") so the initial
  // context hydration sequence (null → firstTeam) is never mistaken for a
  // user-initiated team switch, matching the fix applied to tickets.tsx.
  const prevSelectedTeamRef = useRef<string | null | undefined>(undefined);
  useEffect(() => {
    const prev = prevSelectedTeamRef.current;
    prevSelectedTeamRef.current = teamFilterSelectedTeam;
    // Skip: first run (undefined) and context hydration (null → firstTeam).
    if (!prev || prev === teamFilterSelectedTeam) return;
    setParams({ selectedTeam: "", page: "0" });
  }, [teamFilterSelectedTeam, setParams]);

  // --- Formatting helpers ---
  const formatDate = (isoString?: string) =>
    isoString ? new Date(isoString).toLocaleString(undefined, { dateStyle: "short", timeStyle: "short" }) : "-";
  const formatDuration = (start?: string, end?: string) => {
    if (!start) return "-";
    const endTime = end ? new Date(end).getTime() : now;
    const durationMs = endTime - new Date(start).getTime();
    const minutes = Math.floor(durationMs / 1000 / 60);
    const hours = Math.floor(minutes / 60);
    const mins = minutes % 60;
    return hours > 0 ? `${hours}h ${mins}m` : `${mins}m`;
  };
  const getDurationColor = (minutes: number) =>
    minutes < 30 ? "text-success" : minutes < 120 ? "text-warning" : "text-destructive font-semibold";

  // --- Date range for date filter ---
  // Empty string dateFilter (= "Any Date") is not in presetDays so falls through to
  // { from: undefined, to: undefined } inside getDateRangeFromFilter, which is correct.
  const dateRange = useMemo(
    () =>
      getDateRangeFromFilter({
        dateFilter,
        customDateRange: { start: params.dateFrom || undefined, end: params.dateTo || undefined },
        customValue: "custom",
        fallbackValue: "lastWeek",
        presetDays: {
          lastWeek: PRESET_DAYS.lastWeek,
          last2Weeks: PRESET_DAYS.last2Weeks,
          lastMonth: PRESET_DAYS.lastMonth,
        },
      }),
    [dateFilter, params.dateFrom, params.dateTo]
  );

  // --- Filtered Escalations ---
  const filteredEscalations = useMemo(() => {
    if (!escalationsData?.content) return [];
    if (hasNoTeamScope) return [];
    let baseEscalations = escalationsData.content;

    // Team filter modes:
    // - ''                => Current Team Scope (use effectiveTeams)
    // - '__all__'         => All Teams override
    // - specific team     => explicit team override
    if (selectedTeam !== ALL_TEAMS_FILTER && !selectedTeam && effectiveTeams.length > 0) {
      baseEscalations = baseEscalations.filter((esc) => {
        if (!esc.escalatingTeam) return false;
        const escalatingTeamKey = normalizeTeamKey(esc.escalatingTeam);
        return effectiveTeams.some((team) => normalizeTeamKey(team) === escalatingTeamKey);
      });
    }

    // Apply explicit page team filter by escalating team (ticket owner / tenant team)
    // Use case-insensitive matching for robustness
    baseEscalations = baseEscalations.filter((esc) => {
      if (!selectedTeam || selectedTeam === ALL_TEAMS_FILTER) return true;
      if (!esc.escalatingTeam) return false;
      return normalizeTeamKey(esc.escalatingTeam) === normalizeTeamKey(selectedTeam);
    });

    // Apply status filter
    if (statusFilter === "ongoing") {
      baseEscalations = baseEscalations.filter((esc) => !esc.resolvedAt);
    } else if (statusFilter === "resolved") {
      baseEscalations = baseEscalations.filter((esc) => esc.resolvedAt);
    }

    // Apply impact filter
    if (impactFilter !== "all") {
      baseEscalations = baseEscalations.filter((esc) => esc.impact === impactFilter);
    }

    // Apply tag filter
    if (tagFilter) {
      baseEscalations = baseEscalations.filter((esc) => esc.tags?.includes(tagFilter));
    }

    // Apply date filter
    if (dateRange.from) {
      const from = new Date(dateRange.from + "T00:00:00");
      baseEscalations = baseEscalations.filter((esc) => esc.openedAt && new Date(esc.openedAt) >= from);
    }
    if (dateRange.to) {
      const to = new Date(dateRange.to + "T23:59:59");
      baseEscalations = baseEscalations.filter((esc) => esc.openedAt && new Date(esc.openedAt) <= to);
    }

    // When viewing as escalation team in default "Current Team Scope", deduplicate by ticketId
    // to match the home dashboard "Tickets We Own - Escalated" count.
    // Do not dedupe explicit tenant/all-team overrides: those should show raw escalation rows.
    const isCurrentScopeView = !selectedTeam;
    if (isViewingAsEscalationTeam && !hasFullAccess && isCurrentScopeView) {
      const ticketMap = new Map<string, (typeof baseEscalations)[0]>();
      baseEscalations.forEach((esc) => {
        const existing = ticketMap.get(esc.ticketId);
        if (!existing || (esc.openedAt && (!existing.openedAt || esc.openedAt > existing.openedAt))) {
          ticketMap.set(esc.ticketId, esc);
        }
      });
      baseEscalations = Array.from(ticketMap.values());
    }

    return baseEscalations;
  }, [
    escalationsData,
    selectedTeam,
    statusFilter,
    impactFilter,
    tagFilter,
    dateRange,
    effectiveTeams,
    hasNoTeamScope,
    isViewingAsEscalationTeam,
    hasFullAccess,
    ALL_TEAMS_FILTER,
  ]);

  // --- Sorting ---
  const handleSort = (column: SortColumn) => {
    if (sortColumn === column) {
      setParams({ sortDir: sortDirection === "asc" ? "desc" : "asc", page: "0" });
    } else {
      setParams({ sortBy: column, sortDir: column === "openedAt" || column === "resolvedAt" ? "desc" : "asc", page: "0" });
    }
  };

  const sortedEscalations = useMemo(() => {
    return [...filteredEscalations].sort((a, b) => {
      let cmp = 0;
      if (sortColumn === "ticketId") {
        cmp = (a.ticketId || "").localeCompare(b.ticketId || "");
      } else if (sortColumn === "escalatingTeam") {
        cmp = (a.escalatingTeam || "").localeCompare(b.escalatingTeam || "");
      } else if (sortColumn === "escalatedTo") {
        cmp = (a.team?.label || a.team?.name || "").localeCompare(b.team?.label || b.team?.name || "");
      } else if (sortColumn === "openedAt") {
        cmp = (a.openedAt || "").localeCompare(b.openedAt || "");
      } else if (sortColumn === "resolvedAt") {
        // Unresolved (null) sorts last regardless of direction
        if (!a.resolvedAt && !b.resolvedAt) cmp = 0;
        else if (!a.resolvedAt) cmp = 1;
        else if (!b.resolvedAt) cmp = -1;
        else cmp = a.resolvedAt.localeCompare(b.resolvedAt);
      } else if (sortColumn === "duration") {
        const aDur = a.openedAt ? (a.resolvedAt ? new Date(a.resolvedAt).getTime() : now) - new Date(a.openedAt).getTime() : 0;
        const bDur = b.openedAt ? (b.resolvedAt ? new Date(b.resolvedAt).getTime() : now) - new Date(b.openedAt).getTime() : 0;
        cmp = aDur - bDur;
      }
      return sortDirection === "asc" ? cmp : -cmp;
    });
  }, [filteredEscalations, sortColumn, sortDirection, now]);

  // --- Pagination ---
  const paginatedEscalations = useMemo(() => {
    const start = pageIndex * pageSize;
    return sortedEscalations.slice(start, start + pageSize);
  }, [sortedEscalations, pageIndex]);

  const totalPages = Math.ceil(filteredEscalations.length / pageSize);

  // --- Top 5 tags ---
  const topTags = useMemo(() => {
    const freqMap: Record<string, number> = {};
    filteredEscalations.forEach((esc) => esc.tags?.forEach((tag) => (freqMap[tag] = (freqMap[tag] || 0) + 1)));
    return Object.entries(freqMap)
      .sort((a, b) => b[1] - a[1])
      .slice(0, 5)
      .map(([tag, count]) => ({ tag, count }));
  }, [filteredEscalations]);

  const teamLabel = (code?: string | null): string => (tenantTeamsData ?? []).find((t) => t.name === code)?.label || code || "";
  const secondTableTitle =
    selectedTeam === ALL_TEAMS_FILTER
      ? "All Escalations"
      : selectedTeam
        ? `Escalated for ${teamLabel(selectedTeam)}`
        : isViewingAsEscalationTeam && teamFilterSelectedTeam
          ? `Escalated for ${teamLabel(teamFilterSelectedTeam)}`
          : "All Escalations";
  const scopeLabel = effectiveTeams.length === 0 ? "All Teams" : effectiveTeams.map(teamLabel).join(", ");
  const teamFilterLabel = selectedTeam === ALL_TEAMS_FILTER ? "All Teams" : teamLabel(selectedTeam) || "Current Team Scope";
  const topTagsTitleSuffix = selectedTeam ? (selectedTeam === ALL_TEAMS_FILTER ? "for All Teams" : `for ${teamLabel(selectedTeam)}`) : "";
  const showEscalatedForColumn = hasFullAccess || selectedTeam === ALL_TEAMS_FILTER;

  const ANY_DATE = "__any";
  const fromAny = (v: string) => (v === ANY_DATE ? "" : v);
  const toAny = (v: string) => (v === "" ? ANY_DATE : v);

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-foreground text-2xl font-bold">Escalations</h1>
          <p className="text-muted-foreground text-sm">Tickets escalated to your team</p>
        </div>
      </div>
      {hasNoTeamScope && (
        <div className="border-warning/30 bg-warning/10 text-warning rounded-lg border p-4">
          <p className="font-semibold">No Team Access</p>
          <p className="mt-1 text-sm">You are not assigned to any teams, so escalations cannot be displayed.</p>
        </div>
      )}
      {isLoadingEscalations && <LoadingSkeleton />}
      {errorEscalations && (
        <div className="border-destructive/30 bg-destructive/10 text-destructive rounded-lg border p-4">
          <p className="font-semibold">Error loading escalations</p>
          <p className="mt-1 text-sm">Unable to load escalation data. Please try refreshing the page.</p>
        </div>
      )}
      {/* Escalated to My Team Table (Escalation Team Only) */}
      <EscalatedToMyTeamTable />
      {/* All Escalations / Escalated for My Team Section */}
      <div data-testid="escalations-main-section" className="bg-card space-y-4 rounded-xl border p-6">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 className="text-foreground text-base font-semibold">{secondTableTitle}</h2>
            {!hasNoTeamScope && (
              <p className="text-muted-foreground mt-1 text-xs">
                Scope: {scopeLabel} · Team Filter: {teamFilterLabel}
              </p>
            )}
          </div>
          <div className="flex items-center gap-2">
            <Select
              value={toAny(dateFilter)}
              onValueChange={(v) => {
                const next = fromAny(v) as EscalationDateFilter;
                setParams(next !== "custom" ? { dateFilter: next, dateFrom: "", dateTo: "", page: "0" } : { dateFilter: next, page: "0" });
              }}
            >
              <SelectTrigger className="w-[160px]" data-testid="escalations-date-filter">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ANY_DATE}>Any Date</SelectItem>
                <SelectItem value="lastWeek">Last Week</SelectItem>
                <SelectItem value="last2Weeks">Last 2 Weeks</SelectItem>
                <SelectItem value="lastMonth">Last Month</SelectItem>
                <SelectItem value="custom">Custom Range</SelectItem>
              </SelectContent>
            </Select>
            {dateFilter === "custom" && (
              <>
                <Input
                  type="date"
                  aria-label="Date filter start"
                  value={params.dateFrom}
                  onChange={(e) => setParams({ dateFrom: e.target.value, page: "0" })}
                  className="w-[150px]"
                />
                <Input
                  type="date"
                  aria-label="Date filter end"
                  value={params.dateTo}
                  onChange={(e) => setParams({ dateTo: e.target.value, page: "0" })}
                  className="w-[150px]"
                />
              </>
            )}
          </div>
        </div>

        {/* Faceted single-value filters */}
        <div className="flex flex-wrap items-center gap-2">
          <SingleSelectFilter
            title="Status"
            value={statusFilter !== "all" ? statusFilter : undefined}
            onChange={(v) => setParams({ status: v ?? "all", page: "0" })}
            showSearch={false}
            options={[
              { label: "Ongoing", value: "ongoing" },
              { label: "Resolved", value: "resolved" },
            ]}
          />
          <SingleSelectFilter
            title="Impact"
            value={impactFilter !== "all" ? impactFilter : undefined}
            onChange={(v) => setParams({ impact: v ?? "all", page: "0" })}
            options={(registryData?.impacts ?? [])
              .filter((impact: { code: string; label: string; active?: boolean }) => impact.active !== false)
              .map((impact) => ({
                label: impact.label,
                value: impact.code,
              }))}
          />
          {!hasNoTeamScope && (
            <SingleSelectFilter
              title="Tenant Team"
              value={selectedTeam || undefined}
              onChange={(v) => setParams({ selectedTeam: v ?? "", page: "0" })}
              options={[
                ...(effectiveTeams.length > 0 ? [{ label: "All Teams", value: ALL_TEAMS_FILTER }] : []),
                ...(tenantTeamsData ?? []).map((team) => ({ label: team.label || team.name, value: team.name })),
              ]}
            />
          )}
          <SingleSelectFilter
            title="Tag"
            value={tagFilter || undefined}
            onChange={(v) => setParams({ tag: v ?? "", page: "0" })}
            options={(registryData?.tags ?? [])
              .filter((tag: { code: string; label: string; active?: boolean }) => tag.active !== false)
              .map((tag) => ({
                label: tag.label,
                value: tag.code,
              }))}
          />
        </div>

        {/* Top 5 Tags */}
        <div className="mb-4">
          <h3 className="text-muted-foreground mb-2 text-sm font-semibold tracking-wide uppercase">
            Top 5 Tags {topTagsTitleSuffix ? ` ${topTagsTitleSuffix}` : ""}
          </h3>
          {topTags.length === 0 ? (
            <p className="text-muted-foreground text-sm">No tags found.</p>
          ) : (
            <div className="flex flex-col gap-1.5">
              {topTags.map(({ tag, count }, idx) => {
                const maxCount = topTags[0].count;
                const widthPercent = Math.min(100, (count / maxCount) * 100);
                return (
                  <div key={tag} className="flex items-center gap-3">
                    <Badge variant="outline" className="shrink-0">
                      {tag}
                    </Badge>
                    <div className="bg-muted h-4 flex-1 overflow-hidden rounded-full">
                      <div
                        className="bg-primary text-primary-foreground flex h-4 items-center justify-end rounded-full pr-2 font-mono text-xs font-semibold tabular-nums transition-all duration-500"
                        style={{ width: `${widthPercent}%` }}
                      >
                        {count}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {filteredEscalations.length === 0 && !isLoadingEscalations && <p>No escalations found.</p>}

        {/* Escalations table */}
        <div className="overflow-x-auto rounded-lg border">
          <table className="min-w-full divide-y">
            <thead className="bg-muted">
              <tr>
                <SortableHeader
                  col="ticketId"
                  label="Ticket ID"
                  sortColumn={sortColumn}
                  sortDirection={sortDirection}
                  onSort={handleSort}
                />
                {showEscalatedForColumn && (
                  <SortableHeader
                    col="escalatingTeam"
                    label="Escalated For"
                    sortColumn={sortColumn}
                    sortDirection={sortDirection}
                    onSort={handleSort}
                  />
                )}
                <SortableHeader
                  col="escalatedTo"
                  label="Escalated To"
                  sortColumn={sortColumn}
                  sortDirection={sortDirection}
                  onSort={handleSort}
                />
                <th className="text-foreground px-4 py-2 text-left text-xs font-bold uppercase">Status</th>
                <th className="text-foreground px-4 py-2 text-left text-xs font-bold uppercase">Impact</th>
                <SortableHeader col="openedAt" label="Opened" sortColumn={sortColumn} sortDirection={sortDirection} onSort={handleSort} />
                <SortableHeader
                  col="resolvedAt"
                  label="Resolved"
                  sortColumn={sortColumn}
                  sortDirection={sortDirection}
                  onSort={handleSort}
                />
                <SortableHeader col="duration" label="Duration" sortColumn={sortColumn} sortDirection={sortDirection} onSort={handleSort} />
                <th className="text-foreground px-4 py-2 text-left text-xs font-bold uppercase">Tags</th>
                <th className="text-foreground px-4 py-2 text-left text-xs font-bold uppercase">Thread</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {paginatedEscalations.map((esc) => {
                const durationMs = esc.openedAt
                  ? (esc.resolvedAt ? new Date(esc.resolvedAt).getTime() : now) - new Date(esc.openedAt).getTime()
                  : 0;
                const durationMinutes = Math.floor(durationMs / 1000 / 60);
                return (
                  <tr key={esc.id} className="hover:bg-accent transition-colors">
                    <td className="px-4 py-2 text-sm">{esc.ticketId}</td>
                    {showEscalatedForColumn && <td className="px-4 py-2 text-sm">{teamLabel(esc.escalatingTeam) || "-"}</td>}
                    <td className="px-4 py-2 text-sm">{esc.team?.label || esc.team?.name || "-"}</td>
                    <td className="px-4 py-2">
                      <span
                        className={`rounded-full px-2 py-1 text-xs font-semibold ${esc.resolvedAt ? "bg-success/20 text-success" : "bg-warning/20 text-warning"}`}
                      >
                        {esc.resolvedAt ? "Resolved" : "Unresolved"}
                      </span>
                    </td>
                    <td className="px-4 py-2">
                      <span className="text-sm font-medium">{esc.impact || "-"}</span>
                    </td>
                    <td className="px-4 py-2 text-sm">{formatDate(esc.openedAt)}</td>
                    <td className="px-4 py-2 text-sm">{esc.resolvedAt ? formatDate(esc.resolvedAt) : "Ongoing"}</td>
                    <td className={`px-4 py-2 text-sm ${getDurationColor(durationMinutes)}`}>
                      {formatDuration(esc.openedAt, esc.resolvedAt ?? undefined)}
                    </td>
                    <td className="px-4 py-2 text-sm">
                      {esc.tags?.length
                        ? esc.tags.map((tag, i) => (
                            <Badge key={i} variant="outline" className="mr-1">
                              {tag}
                            </Badge>
                          ))
                        : "-"}
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
                      ) : (
                        "-"
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        {/* Pagination buttons */}
        {filteredEscalations.length > pageSize && (
          <div className="mt-4 flex items-center justify-between gap-4">
            <Button variant="outline" onClick={() => setParams({ page: String(Math.max(pageIndex - 1, 0)) })} disabled={pageIndex === 0}>
              Previous
            </Button>
            <span className="text-muted-foreground text-sm">
              Page {pageIndex + 1} of {totalPages}
            </span>
            <Button
              variant="outline"
              onClick={() => setParams({ page: String(Math.min(pageIndex + 1, totalPages - 1)) })}
              disabled={pageIndex >= totalPages - 1}
            >
              Next
            </Button>
          </div>
        )}
      </div>{" "}
      {/* End of purple container */}
    </div>
  );
}
