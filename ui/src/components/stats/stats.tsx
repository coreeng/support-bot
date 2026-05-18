"use client";

import { HorizontalBarChart } from "@/components/dashboards/HorizontalBarChart";
import { TimeSeriesChart } from "@/components/dashboards/TimeSeriesChart";
import EscalatedToMyTeamWidget from "@/components/escalations/EscalatedToMyTeamWidget";
import LoadingSkeleton from "@/components/LoadingSkeleton";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useTeamFilter } from "@/contexts/TeamFilterContext";
import { useAuth } from "@/hooks/useAuth";
import { TEAM_SCOPE } from "@/lib/constants";
import { type DateFilter, getDateRangeFromFilter, PRESET_DAYS } from "@/lib/dateRange";
import { useAllTickets, useIncomingVsResolvedRate, useRegistry } from "@/lib/hooks";
import { enumValidator, isoDateValidator, useUrlParams } from "@/lib/hooks/useUrlParams";
import { formatIncomingVsResolvedSeries } from "@/lib/incomingVsResolved";
import { normalizeTeamKey } from "@/lib/teamUtils";
import { type TicketImpact, type TicketWithLogs } from "@/lib/types";
import type { IncomingVsResolvedRatePoint } from "@/lib/types/dashboard";
import { type JSX, useEffect, useMemo } from "react";

const VALID_DATE_FILTERS = ["lastWeek", "last2Weeks", "lastMonth", "lastYear", "custom", "all"] as const satisfies readonly DateFilter[];

function StatTile({ label, value, valueClass }: { label: string; value: number; valueClass?: string }) {
  return (
    <div className="bg-card rounded-xl border p-5">
      <p className="text-muted-foreground text-sm font-medium">{label}</p>
      <p className={`mt-2 font-mono text-3xl font-semibold tracking-tight tabular-nums ${valueClass ?? "text-foreground"}`}>{value}</p>
    </div>
  );
}

type DateFilterControlsProps = {
  dateFilter: DateFilter;
  dateFrom: string;
  dateTo: string;
  onDateFilterChange: (value: DateFilter) => void;
  onDateFromChange: (value: string) => void;
  onDateToChange: (value: string) => void;
};

const INCOMING_RESOLVED_LINES = [
  { dataKey: "incoming", name: "Incoming", color: "var(--chart-5)" },
  { dataKey: "resolved", name: "Resolved", color: "var(--chart-2)" },
];

function getIncomingResolvedTeamCodes(
  effectiveTeams: string[],
  userTeams: Array<{ name: string; label: string; code: string }> = []
): string[] {
  const teamCodeByScopeKey = new Map(
    userTeams.flatMap((team) => [
      [normalizeTeamKey(team.name), team.code],
      [normalizeTeamKey(team.label), team.code],
      [normalizeTeamKey(team.code), team.code],
    ])
  );

  return effectiveTeams
    .filter((team) => team !== TEAM_SCOPE.NO_TEAMS)
    .map((team) => {
      const code = teamCodeByScopeKey.get(normalizeTeamKey(team));
      if (!code) {
        console.warn(`getIncomingResolvedTeamCodes: could not resolve team "${team}" to a code`);
      }
      return code;
    })
    .filter((team): team is string => !!team);
}

function renderIncomingResolvedChart({
  hasNoTeamScope,
  hasDashboardAccess,
  isLoading,
  error,
  data,
}: {
  hasNoTeamScope: boolean;
  hasDashboardAccess: boolean;
  isLoading: boolean;
  error: Error | null;
  data: IncomingVsResolvedRatePoint[];
}): JSX.Element {
  // No-team scope: show the chart empty state (not the role-restricted panel), which matches the
  // dashboard banner that already explains missing team access.
  if (!hasDashboardAccess && !hasNoTeamScope) {
    return (
      <div className="bg-card rounded-xl border p-6">
        <h3 className="text-muted-foreground text-sm font-medium">Incoming vs Resolved</h3>
        <p className="text-muted-foreground mt-3 text-sm">This chart requires Support Engineer or Leadership access.</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="border-destructive/30 bg-card rounded-xl border p-6">
        <h3 className="text-muted-foreground text-sm font-medium">Incoming vs Resolved</h3>
        <p className="text-destructive mt-3 text-sm">Unable to load incoming and resolved ticket activity.</p>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="bg-card rounded-xl border p-6">
        <h3 className="text-muted-foreground text-sm font-medium">Incoming vs Resolved</h3>
        <p className="text-muted-foreground mt-3 text-sm">Loading incoming and resolved ticket activity...</p>
      </div>
    );
  }

  const emptyMessage = hasNoTeamScope
    ? "Incoming and resolved ticket activity cannot be shown without team access."
    : "No incoming or resolved ticket activity for the selected date range.";
  return (
    <TimeSeriesChart
      title="Incoming vs Resolved"
      data={hasNoTeamScope ? [] : data}
      lines={INCOMING_RESOLVED_LINES}
      xAxisDataKey="time"
      yAxisLabel="Tickets"
      tooltipFormatter={(value, name) => [`${value} ${value === 1 ? "ticket" : "tickets"}`, name]}
      height={280}
      showLegend={true}
      emptyMessage={emptyMessage}
    />
  );
}

function DateFilterControls({
  dateFilter,
  dateFrom,
  dateTo,
  onDateFilterChange,
  onDateFromChange,
  onDateToChange,
}: DateFilterControlsProps): JSX.Element {
  return (
    <div className="flex items-center gap-2">
      <Select value={dateFilter} onValueChange={(value) => onDateFilterChange(value as DateFilter)}>
        <SelectTrigger className="w-[160px]">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="lastWeek">Last Week</SelectItem>
          <SelectItem value="last2Weeks">Last 2 Weeks</SelectItem>
          <SelectItem value="lastMonth">Last Month</SelectItem>
          <SelectItem value="lastYear">Last Year</SelectItem>
          <SelectItem value="custom">Custom Range</SelectItem>
          <SelectItem value="all">All Time</SelectItem>
        </SelectContent>
      </Select>
      {dateFilter === "custom" && (
        <>
          <Input type="date" value={dateFrom} onChange={(e) => onDateFromChange(e.target.value)} className="w-[160px]" />
          <span className="text-muted-foreground">to</span>
          <Input type="date" value={dateTo} onChange={(e) => onDateToChange(e.target.value)} className="w-[160px]" />
        </>
      )}
    </div>
  );
}

function filterTicketsByScope(tickets: TicketWithLogs[], effectiveTeams: string[], hasNoTeamScope: boolean): TicketWithLogs[] {
  if (hasNoTeamScope) {
    return [];
  }

  if (effectiveTeams.length === 0) {
    return tickets;
  }

  return tickets.filter((ticket) => {
    if (!ticket.team?.name) {
      return false;
    }

    const ticketTeam = normalizeTeamKey(ticket.team.name);
    return effectiveTeams.some((team) => normalizeTeamKey(team) === ticketTeam);
  });
}

export default function StatsPage() {
  // Persist date filter and custom date range in the URL.
  // Validators guard against invalid URL values and auto-correct the URL.
  const [params, setParams] = useUrlParams(
    { dateFilter: "lastWeek", dateFrom: "", dateTo: "" },
    { dateFilter: enumValidator(VALID_DATE_FILTERS, "lastWeek"), dateFrom: isoDateValidator, dateTo: isoDateValidator }
  );

  // Safe to cast: enumValidator guarantees params.dateFilter is a valid DateFilter.
  const dateFilter = params.dateFilter as DateFilter;
  const isAllTime = dateFilter === "all";

  // Correct the URL when custom date range is in an invalid order (dateFrom > dateTo).
  useEffect(() => {
    if (params.dateFilter === "custom" && params.dateFrom && params.dateTo && params.dateFrom > params.dateTo) {
      setParams({ dateFilter: "lastWeek", dateFrom: "", dateTo: "" });
    }
  }, [params.dateFilter, params.dateFrom, params.dateTo, setParams]);

  // Calculate date range based on filter using the shared utility.
  // Falls back to lastWeek when custom dates are not yet set.
  const dateRange = useMemo(
    () =>
      getDateRangeFromFilter({
        dateFilter,
        customDateRange: { start: params.dateFrom || undefined, end: params.dateTo || undefined },
        customValue: "custom",
        fallbackValue: "lastWeek",
        allValue: "all",
        presetDays: PRESET_DAYS,
      }),
    [dateFilter, params.dateFrom, params.dateTo]
  );

  const { data: ticketsData, isLoading: isTicketsLoading, error: ticketsError } = useAllTickets(200, dateRange.from, dateRange.to);
  const { data: registryData } = useRegistry();
  const {
    effectiveTeams,
    hasNoTeamScope: contextHasNoTeamScope,
    selectedTeam,
    isViewingAsEscalationTeam: contextIsViewingAsEscalationTeam,
  } = useTeamFilter();
  const { user, actualEscalationTeams, isLeadership, isSupportEngineer } = useAuth();
  const hasDashboardAccess = isLeadership || isSupportEngineer;
  const hasNoTeamScope = contextHasNoTeamScope ?? effectiveTeams.includes(TEAM_SCOPE.NO_TEAMS);
  const isViewingAsEscalationTeam = contextIsViewingAsEscalationTeam ?? (!!selectedTeam && actualEscalationTeams.includes(selectedTeam));
  const chartTeams = useMemo(() => getIncomingResolvedTeamCodes(effectiveTeams, user?.teams ?? []), [effectiveTeams, user?.teams]);
  const {
    data: incomingResolvedRate,
    isLoading: isIncomingResolvedLoading,
    error: incomingResolvedError,
  } = useIncomingVsResolvedRate(!hasNoTeamScope && hasDashboardAccess, isAllTime ? undefined : dateRange.from, dateRange.to, {
    teams: chartTeams,
    allTime: isAllTime,
    granularity: "AUTO",
  });
  const formattedIncomingResolvedSeries = useMemo(
    () => formatIncomingVsResolvedSeries(incomingResolvedRate?.data ?? [], incomingResolvedRate?.granularity),
    [incomingResolvedRate]
  );

  const teamTickets = useMemo(
    () => filterTicketsByScope(ticketsData?.content ?? [], effectiveTeams, hasNoTeamScope),
    [ticketsData, effectiveTeams, hasNoTeamScope]
  );

  const totalTickets = teamTickets.length;
  const openTickets = teamTickets.filter((t) => t.status === "opened").length;
  const resolvedTickets = teamTickets.filter((t) => t.status === "closed").length;
  const escalatedTickets = teamTickets.filter((t) => (t.escalations?.length ?? 0) > 0).length;

  const ticketsByImpact = useMemo(() => {
    const counts: Record<string, number> = {};
    teamTickets.forEach((t) => {
      const impactLabel = registryData?.impacts.find((i: TicketImpact) => i.code === t.impact)?.label;
      if (impactLabel) {
        counts[impactLabel] = (counts[impactLabel] || 0) + 1;
      }
    });
    return Object.entries(counts).map(([name, value]) => ({ name, value }));
  }, [teamTickets, registryData]);

  const incomingResolvedChart = renderIncomingResolvedChart({
    hasNoTeamScope,
    hasDashboardAccess,
    isLoading: isIncomingResolvedLoading,
    error: incomingResolvedError,
    data: formattedIncomingResolvedSeries,
  });

  const handleDateFilterChange = (next: DateFilter) => {
    if (next === "custom") {
      setParams({ dateFilter: next });
      return;
    }

    setParams({ dateFilter: next, dateFrom: "", dateTo: "" });
  };

  const dateFilterControls = (
    <DateFilterControls
      dateFilter={dateFilter}
      dateFrom={params.dateFrom}
      dateTo={params.dateTo}
      onDateFilterChange={handleDateFilterChange}
      onDateFromChange={(dateFrom) => setParams({ dateFrom })}
      onDateToChange={(dateTo) => setParams({ dateTo })}
    />
  );

  const impactChart = (
    <HorizontalBarChart
      title="Tickets by Impact"
      data={ticketsByImpact}
      dataKey="value"
      yAxisDataKey="name"
      color="var(--chart-4)"
      tooltipFormatter={(value) => [`${value} ${value === 1 ? "ticket" : "tickets"}`, ""]}
      tooltipLabelFormatter={() => ""}
      tooltipSeparator=""
      height={280}
    />
  );

  if (isTicketsLoading) return <LoadingSkeleton />;
  if (ticketsError)
    return (
      <div className="border-destructive/30 bg-destructive/10 text-destructive rounded-lg border p-4">
        <p className="font-semibold">Error loading dashboard</p>
        <p className="mt-1 text-sm">Unable to load dashboard data. Please try refreshing the page.</p>
      </div>
    );

  if (isViewingAsEscalationTeam) {
    return (
      <div className="space-y-8">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h1 className="text-foreground text-2xl font-bold">Support Dashboard</h1>
            <p className="text-muted-foreground text-sm">Overview of your team&apos;s support load</p>
          </div>
          {dateFilterControls}
        </div>

        <section className="space-y-4">
          <h2 className="text-foreground text-base font-semibold">Escalations We Are Handling</h2>
          <EscalatedToMyTeamWidget />
        </section>

        <section className="space-y-4">
          <h2 className="text-foreground text-base font-semibold">Tickets We Own</h2>
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">
            <StatTile label="Total Tickets" value={totalTickets} />
            <StatTile label="Open Tickets" value={openTickets} valueClass="text-warning" />
            <StatTile label="Escalated Tickets" value={escalatedTickets} valueClass="text-destructive" />
            <StatTile label="Resolved Tickets" value={resolvedTickets} valueClass="text-success" />
          </div>
          <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
            {incomingResolvedChart}
            {impactChart}
          </div>
        </section>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-foreground text-2xl font-bold">Support Dashboard</h1>
          <p className="text-muted-foreground text-sm">Overview of your team&apos;s support load</p>
        </div>
        {dateFilterControls}
      </div>

      {hasNoTeamScope && (
        <div className="border-warning/30 bg-warning/10 text-warning rounded-lg border p-4">
          <p className="font-semibold">No Team Access</p>
          <p className="mt-1 text-sm">You are not assigned to any teams, so dashboard data cannot be displayed.</p>
        </div>
      )}

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">
        <StatTile label="Total Tickets" value={totalTickets} />
        <StatTile label="Open Tickets" value={openTickets} valueClass="text-warning" />
        <StatTile label="Escalated Tickets" value={escalatedTickets} valueClass="text-destructive" />
        <StatTile label="Resolved Tickets" value={resolvedTickets} valueClass="text-success" />
      </div>

      <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
        {incomingResolvedChart}
        {impactChart}
      </div>
    </div>
  );
}
