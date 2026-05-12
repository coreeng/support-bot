"use client";

import LoadingSkeleton from "@/components/LoadingSkeleton";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { SingleSelectFilter } from "@/components/ui/single-select-filter";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { getDateRangeFromFilter, PRESET_DAYS } from "@/lib/dateRange";
import { useAssignmentEnabled, useRatings, useRegistry, useSupportMembers, useTickets } from "@/lib/hooks";
import { enumValidator, isoDateValidator, useUrlParams } from "@/lib/hooks/useUrlParams";
import {
  AggregatedTicketStats,
  BulkReassignRequest,
  BulkReassignResult,
  Escalation,
  ParsedTicketLog,
  SupportMember,
  TicketImpact,
  TicketLog,
  TicketWithLogs,
} from "@/lib/types";
import { useQueryClient } from "@tanstack/react-query";
import { AlertTriangle, ChevronDown, ClipboardList, Headphones, Star } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Bar, BarChart, CartesianGrid, Cell, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";

export default function HealthPage() {
  const { data: registryData } = useRegistry();
  const { data: supportMembers } = useSupportMembers();
  const { data: isAssignmentEnabled } = useAssignmentEnabled();
  const queryClient = useQueryClient();

  const tabs = [
    { key: "tickets" as const, label: "Activity Trends", icon: ClipboardList, color: "blue" },
    { key: "ratings" as const, label: "Ratings", icon: Star, color: "yellow" },
    { key: "workbench" as const, label: "Ticket Workbench", icon: Headphones, color: "purple" },
  ];

  // Persist date filter mode, custom date range, and active tab in the URL.
  // Validators guard against invalid URL values and auto-correct the URL.
  const [params, setParams] = useUrlParams(
    { dateFilter: "lastWeek", dateFrom: "", dateTo: "", tab: "tickets" },
    {
      dateFilter: enumValidator(["lastWeek", "last2Weeks", "lastMonth", "lastYear", "custom"] as const, "lastWeek"),
      dateFrom: isoDateValidator,
      dateTo: isoDateValidator,
      tab: enumValidator(["tickets", "ratings", "workbench"] as const, "tickets"),
    }
  );
  // Safe to cast: validators guarantee these are valid enum values.
  const dateFilter = params.dateFilter as "lastWeek" | "last2Weeks" | "lastMonth" | "lastYear" | "custom";
  const activeTab = params.tab as "tickets" | "ratings" | "workbench";

  // Correct the URL when custom date range is in an invalid order (dateFrom > dateTo).
  useEffect(() => {
    if (params.dateFilter === "custom" && params.dateFrom && params.dateTo && params.dateFrom > params.dateTo) {
      setParams({ dateFilter: "lastWeek", dateFrom: "", dateTo: "" });
    }
  }, [params.dateFilter, params.dateFrom, params.dateTo, setParams]);

  const [inquiringTeamFilter, setInquiringTeamFilter] = useState("");
  const [escalatedTeamFilter, setEscalatedTeamFilter] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [ratedFilter, setRatedFilter] = useState("");
  const [assigneeFilter, setAssigneeFilter] = useState("");
  const [bulkReassignFrom, setBulkReassignFrom] = useState("");
  const [bulkReassignTo, setBulkReassignTo] = useState("");
  const [isReassigning, setIsReassigning] = useState(false);
  const [reassignMessage, setReassignMessage] = useState<{ type: "success" | "error"; text: string } | null>(null);
  const [showConfirmation, setShowConfirmation] = useState(false);
  const [confirmationDetails, setConfirmationDetails] = useState<{
    from: string;
    to: string;
    count: number;
    tickets: TicketWithLogs[];
  } | null>(null);
  const [bulkReassignExpanded, setBulkReassignExpanded] = useState(false);
  const [capacityInsightsExpanded, setCapacityInsightsExpanded] = useState(false);

  const [engineersOnRota, setEngineersOnRota] = useState<number>(2); // Default to 2, configurable
  const [ticketsPerEngineerCapacity, setTicketsPerEngineerCapacity] = useState<number>(5); // Default to 5, configurable

  const now = useMemo(() => new Date(), []);
  // Valid when both are empty (no custom dates set yet) or start ≤ end.
  const isDateRangeValid = !params.dateFrom || !params.dateTo || params.dateFrom <= params.dateTo;

  // Calculate date range based on filter mode using the shared utility.
  // Falls back to lastWeek when custom dates are absent or invalid.
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
          lastYear: PRESET_DAYS.lastYear,
        },
      }),
    [dateFilter, params.dateFrom, params.dateTo]
  );

  const { data: tickets, isLoading: ticketsLoading } = useTickets(0, 1000, dateRange.from, dateRange.to);

  // Count actual weekdays (Mon-Fri) in the selected date range. Used as the denominator
  // for avgTickets in capacity insights — the busiestPeriods heatmap accumulates totals
  // across all weeks in the range, so dividing by 5 (DOW count) would over-inflate
  // averages for any range longer than one week.
  const weekdaysInRange = useMemo(() => {
    if (!dateRange.from || !dateRange.to) return 5;
    const start = new Date(dateRange.from + "T12:00:00");
    const end = new Date(dateRange.to + "T12:00:00");
    let count = 0;
    const curr = new Date(start);
    while (curr <= end) {
      const day = curr.getDay();
      if (day >= 1 && day <= 5) count++;
      curr.setDate(curr.getDate() + 1);
    }
    return Math.max(1, count);
  }, [dateRange]);
  const { data: ratingStats, isLoading: ratingsLoading } = useRatings(dateRange.from, dateRange.to);
  const [currentPage, setCurrentPage] = useState(1);
  const ticketsPerPage = 10;
  const COLORS = [
    "var(--chart-1)",
    "var(--chart-2)",
    "var(--chart-3)",
    "var(--chart-4)",
    "var(--chart-9)",
    "var(--chart-10)",
    "var(--chart-8)",
  ];
  const statusColors: Record<string, string> = {
    opened: "bg-info/10 text-primary",
    closed: "bg-success/10 text-success",
  };

  function getOpenedClosed(ticket: { logs?: TicketLog[] }) {
    const logs = Array.isArray(ticket.logs) ? ticket.logs.slice() : [];

    if (!logs.length) return { opened: null, closed: null };

    // Parse and filter logs
    const parsed: ParsedTicketLog[] = logs
      .map((log): ParsedTicketLog | null => {
        const date = log?.date ? new Date(log.date) : null;
        return date && !isNaN(date.getTime()) ? { ...log, parsedDate: date } : null;
      })
      .filter((log): log is ParsedTicketLog => log !== null);

    if (!parsed.length) return { opened: null, closed: null };

    // Sort by parsed date ascending
    parsed.sort((a, b) => a.parsedDate.getTime() - b.parsedDate.getTime());

    const opened = parsed[0].parsedDate;

    // Find the last log that indicates closing/resolving
    const closedLog = [...parsed]
      .reverse()
      .find(
        (log) =>
          log.event.toLowerCase().includes("close") ||
          log.event.toLowerCase().includes("resolve") ||
          log.event.toLowerCase().includes("closed")
      );

    const closed = closedLog ? closedLog.parsedDate : null;

    return { opened, closed };
  }

  const ensureKey = (map: Record<string, AggregatedTicketStats>, key: string): AggregatedTicketStats => {
    if (!map[key]) {
      map[key] = {
        date: key,
        opened: 0,
        closed: 0,
        escalated: 0,
      };
    }
    return map[key];
  };

  // Tickets are now filtered by date on the server side
  const filteredTickets = useMemo(() => {
    if (!tickets?.content) return [];

    return tickets.content.filter((t: TicketWithLogs) => {
      const escalations = Array.isArray(t.escalations) ? t.escalations : [];

      const inquiringMatch = inquiringTeamFilter ? t.team?.name === inquiringTeamFilter : true;

      const escalatedMatch = escalatedTeamFilter
        ? escalations.some((e: Escalation) => {
            if (!e.team?.name) return false;
            // Case-insensitive matching for robustness
            return e.team.name.trim().toLowerCase() === escalatedTeamFilter.trim().toLowerCase();
          })
        : true;

      const statusMatch = statusFilter ? t.status?.toLowerCase() === statusFilter.toLowerCase() : true;

      const ratedMatch = ratedFilter ? (ratedFilter === "yes" ? t.ratingSubmitted : !t.ratingSubmitted) : true;

      const assigneeMatch = assigneeFilter ? (assigneeFilter === "unassigned" ? !t.assignedTo : t.assignedTo === assigneeFilter) : true;

      return inquiringMatch && escalatedMatch && statusMatch && ratedMatch && assigneeMatch;
    });
  }, [tickets, inquiringTeamFilter, escalatedTeamFilter, statusFilter, ratedFilter, assigneeFilter]);

  // --- Metrics ---
  const staleTicketsCount = useMemo(() => {
    return filteredTickets.filter((t: TicketWithLogs) => t.status === "stale").length;
  }, [filteredTickets]);

  // Backend now calculates average rating and weekly breakdown
  const avgRating = ratingStats?.average || 0;
  const totalRatings = ratingStats?.count || 0;
  const weeklyRatings = useMemo(() => ratingStats?.weekly || [], [ratingStats]);

  const avgResolutionTimeSecs = useMemo(() => {
    if (!filteredTickets.length) return 0;
    const totalSecs = filteredTickets.reduce((acc, t) => {
      const { opened, closed } = getOpenedClosed(t);
      if (!opened || !closed) return acc;
      return acc + (closed.getTime() - opened.getTime()) / 1000;
    }, 0);
    return totalSecs / filteredTickets.length;
  }, [filteredTickets]);

  const largestActiveTicketSecs = useMemo(() => {
    if (!filteredTickets.length) return 0;
    return Math.max(
      ...filteredTickets.map((t) => {
        const { opened, closed } = getOpenedClosed(t);
        if (!opened) return 0;
        return ((closed || now).getTime() - opened.getTime()) / 1000;
      })
    );
  }, [filteredTickets, now]);

  const percentageRated = useMemo(() => {
    if (!filteredTickets.length) return 0;
    return Math.round((filteredTickets.filter((t) => t.ratingSubmitted).length / filteredTickets.length) * 100);
  }, [filteredTickets]);

  // --- Assignment data: average tickets per support engineer by day ---
  const assignmentsByDay = useMemo(() => {
    if (!isAssignmentEnabled || !supportMembers || supportMembers.length === 0) {
      return [];
    }

    const map: Record<string, { date: string; totalAssignments: number; engineerCount: number }> = {};

    filteredTickets.forEach((t) => {
      if (t.assignedTo) {
        // Use the opened date as the assignment date
        const { opened } = getOpenedClosed(t);
        if (opened) {
          const dateKey = opened.toISOString().split("T")[0];
          if (!map[dateKey]) {
            map[dateKey] = { date: dateKey, totalAssignments: 0, engineerCount: supportMembers.length };
          }
          map[dateKey].totalAssignments++;
        }
      }
    });

    // Calculate average and sort by date
    return Object.values(map)
      .map((entry) => ({
        date: entry.date,
        avgAssignments: entry.engineerCount > 0 ? parseFloat((entry.totalAssignments / entry.engineerCount).toFixed(2)) : 0,
        totalAssignments: entry.totalAssignments,
      }))
      .sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime());
  }, [filteredTickets, isAssignmentEnabled, supportMembers]);

  // --- Timeline data: aggregate by ISO date (YYYY-MM-DD) ---
  const timelineData = useMemo(() => {
    const map: Record<string, { date: string; opened: number; closed: number; escalated: number }> = {};

    filteredTickets.forEach((t) => {
      const { opened, closed } = getOpenedClosed(t);
      const escalations = Array.isArray(t.escalations) ? t.escalations : [];

      if (opened) ensureKey(map, opened.toISOString().split("T")[0]).opened++;
      if (closed) ensureKey(map, closed.toISOString().split("T")[0]).closed++;
      escalations.forEach((esc: Escalation) => {
        const escDate = esc?.openedAt ? new Date(esc.openedAt) : null;
        if (escDate && !isNaN(escDate.getTime())) {
          ensureKey(map, escDate.toISOString().split("T")[0]).escalated++;
        }
      });
    });

    // If there's no data (but there are tickets overall), produce a single point for today (helps avoid empty charts)
    const values = Object.values(map).sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime());
    if (values.length === 0 && filteredTickets.length > 0) {
      const todayKey = now.toISOString().split("T")[0];
      return [{ date: todayKey, opened: 0, closed: 0, escalated: 0 }];
    }
    return values;
  }, [filteredTickets, now]);

  // --- Pagination for tickets table ---
  const paginatedTickets = useMemo(() => {
    const start = (currentPage - 1) * ticketsPerPage;
    return filteredTickets.slice(start, start + ticketsPerPage);
  }, [filteredTickets, currentPage]);

  // Ratings per week from backend (fallback to empty)
  const ratingsByWeek = useMemo(() => {
    return (weeklyRatings || [])
      .map(({ weekStart, count }) => ({
        weekStart,
        count: count ?? 0,
      }))
      .sort((a, b) => new Date(a.weekStart).getTime() - new Date(b.weekStart).getTime());
  }, [weeklyRatings]);

  // --- Tickets opened by requesting team (Top 10) ---
  const ticketsByTeam = useMemo(() => {
    const counts: Record<string, number> = {};
    filteredTickets.forEach((t) => {
      const teamName = t.team?.name || "Unassigned Team";
      counts[teamName] = (counts[teamName] || 0) + 1;
    });
    return Object.entries(counts)
      .map(([name, value]) => ({ name, value }))
      .sort((a, b) => b.value - a.value)
      .slice(0, 10); // Top 10 only
  }, [filteredTickets]);

  // --- Current active tickets per engineer ---
  const activeTicketsPerEngineer = useMemo(() => {
    if (!isAssignmentEnabled || !supportMembers || supportMembers.length === 0) {
      return [];
    }

    const counts: Record<string, number> = {};
    const openTickets = filteredTickets.filter((t) => t.status?.toLowerCase() === "opened");

    openTickets.forEach((t) => {
      if (t.assignedTo) {
        counts[t.assignedTo] = (counts[t.assignedTo] || 0) + 1;
      }
    });

    // Include all engineers, even if they have 0 tickets
    return supportMembers
      .map((member) => ({
        name: member.displayName,
        tickets: counts[member.displayName] || 0,
      }))
      .sort((a, b) => b.tickets - a.tickets);
  }, [filteredTickets, isAssignmentEnabled, supportMembers]);

  // --- Tickets opened by hour of day (7AM-7PM only, using ticket time) ---
  const ticketsByHour = useMemo(() => {
    const counts: Record<number, number> = {};
    // Only track hours 7-19 (7AM to 7PM)
    for (let i = 7; i <= 19; i++) {
      counts[i] = 0;
    }

    filteredTickets.forEach((t) => {
      const { opened } = getOpenedClosed(t);
      if (opened) {
        const hour = opened.getHours(); // Uses ticket's opened time
        // Only count hours between 7AM and 7PM
        if (hour >= 7 && hour <= 19) {
          counts[hour] = (counts[hour] || 0) + 1;
        }
      }
    });

    return Object.entries(counts)
      .map(([hour, count]) => ({
        hour: parseInt(hour),
        hourLabel: `${String(hour).padStart(2, "0")}:00`,
        count,
      }))
      .sort((a, b) => a.hour - b.hour);
  }, [filteredTickets]);

  // --- Busiest periods heatmap (day of week × hour) - Weekdays only, 7AM-6PM, using ticket time ---
  // Hours 7-18 (7:00 AM to 6:59 PM). "7 AM - 7 PM" means the boundary is 19:00, so
  // the last tracked hour slot is 18 (6 PM). This aligns with the 2-hour capacity blocks
  // which cover [start, end) — the last block "5 PM - 7 PM" covers hours 17 and 18.
  const busiestPeriods = useMemo(() => {
    const days = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday"];
    const dayIndexMap: Record<number, string> = {
      1: "Monday",
      2: "Tuesday",
      3: "Wednesday",
      4: "Thursday",
      5: "Friday",
    };
    const heatmap: Record<string, Record<number, number>> = {};

    days.forEach((day) => {
      heatmap[day] = {};
      for (let hour = 7; hour <= 18; hour++) {
        heatmap[day][hour] = 0;
      }
    });

    filteredTickets.forEach((t) => {
      const { opened } = getOpenedClosed(t);
      if (opened) {
        const dayOfWeek = opened.getDay();
        const dayName = dayIndexMap[dayOfWeek];
        if (dayName) {
          const hour = opened.getHours();
          if (hour >= 7 && hour <= 18) {
            heatmap[dayName][hour] = (heatmap[dayName][hour] || 0) + 1;
          }
        }
      }
    });

    return days.map((day) => {
      const row: { day: string; [key: number]: number } = { day };
      for (let hour = 7; hour <= 18; hour++) {
        row[hour] = heatmap[day][hour];
      }
      return row;
    });
  }, [filteredTickets]);

  // --- Capacity Insights by 2-Hour Blocks ---
  const capacityInsights = useMemo(() => {
    if (!busiestPeriods.length) {
      return [];
    }

    const rotaCount = Math.max(1, engineersOnRota);
    const capacityPerEngineer = Math.max(1, ticketsPerEngineerCapacity);
    const currentCapacity = rotaCount * capacityPerEngineer;

    const formatHour = (h: number) => {
      if (h < 12) return `${h} AM`;
      if (h === 12) return "12 PM";
      return `${h - 12} PM`;
    };

    // Group hours into 2-hour blocks: 7-9, 9-11, 11-13, 13-15, 15-17, 17-19
    const timeBlocks: Array<{ start: number; end: number; label: string }> = [];
    for (let start = 7; start < 19; start += 2) {
      const end = start + 2;
      timeBlocks.push({
        start,
        end,
        label: `${formatHour(start)} - ${formatHour(end)}`,
      });
    }

    const insights: Array<{
      timeBlock: string;
      totalTickets: number;
      avgPerWeekday: number;
      currentCapacity: number;
      utilization: number;
      recommendedRange: string;
      status: "over" | "near" | "under";
    }> = [];

    timeBlocks.forEach((block) => {
      // Aggregate tickets across all weekdays for this 2-hour block
      let totalTicketsInBlock = 0;
      busiestPeriods.forEach((row) => {
        for (let hour = block.start; hour < block.end; hour++) {
          totalTicketsInBlock += row[hour] as number;
        }
      });

      // Average tickets per weekday in the selected date range.
      // weekdaysInRange is the actual number of Mon-Fri days in the range (not just 1-5).
      const avgTickets = parseFloat((totalTicketsInBlock / weekdaysInRange).toFixed(2));

      // Capacity for 2-hour block (same as single hour since it's concurrent capacity)
      const utilization = currentCapacity > 0 ? Math.round((avgTickets / currentCapacity) * 100) : 0;

      // Calculate recommended engineers (as range)
      const minRecommended = Math.ceil(avgTickets / capacityPerEngineer);
      const maxRecommended = Math.ceil((avgTickets * 1.2) / capacityPerEngineer); // Add 20% buffer
      const recommendedRange = minRecommended === maxRecommended ? `${minRecommended}` : `${minRecommended}-${maxRecommended}`;

      // Determine status
      let status: "over" | "near" | "under" = "under";
      if (utilization > 100) {
        status = "over";
      } else if (utilization > 80) {
        status = "near";
      }

      insights.push({
        timeBlock: block.label,
        totalTickets: totalTicketsInBlock,
        avgPerWeekday: avgTickets,
        currentCapacity,
        utilization,
        recommendedRange,
        status,
      });
    });

    return insights;
  }, [busiestPeriods, weekdaysInRange, engineersOnRota, ticketsPerEngineerCapacity]);

  // --- Capacity vs Demand ---
  const capacityVsDemand = useMemo(() => {
    const openTickets = filteredTickets.filter((t) => t.status?.toLowerCase() === "opened");
    const ticketsCount = openTickets.length;

    // Use engineersOnRota instead of total support members
    const rotaCount = Math.max(1, engineersOnRota); // Ensure at least 1

    const capacityPerEngineer = Math.max(1, ticketsPerEngineerCapacity); // Ensure at least 1
    const totalCapacity = rotaCount * capacityPerEngineer;

    return {
      engineersOnRota: rotaCount,
      totalEngineers: supportMembers?.length || 0,
      openTickets: ticketsCount,
      ticketsPerEngineer: rotaCount > 0 ? parseFloat((ticketsCount / rotaCount).toFixed(2)) : 0,
      capacityPerEngineer: capacityPerEngineer,
      totalCapacity: totalCapacity,
      capacityUtilization:
        totalCapacity > 0
          ? Math.round((ticketsCount / totalCapacity) * 100) // Can exceed 100% when over capacity
          : 0,
    };
  }, [filteredTickets, engineersOnRota, ticketsPerEngineerCapacity, supportMembers]);

  const avgRatingsByWeek = useMemo(() => {
    return (weeklyRatings || [])
      .map(({ weekStart, average }) => ({
        weekStart,
        average: average ?? 0,
      }))
      .sort((a, b) => new Date(a.weekStart).getTime() - new Date(b.weekStart).getTime());
  }, [weeklyRatings]);

  // --- Teams lists for filters (safe) ---
  const inquiringTeamsWithTickets = useMemo(() => {
    const set = new Set<string>();
    tickets?.content?.forEach((t) => {
      if (t.team?.name) set.add(t.team.name);
    });
    return Array.from(set);
  }, [tickets]);

  const escalatedTeamsWithTickets = useMemo(() => {
    const set = new Set<string>();
    tickets?.content?.forEach((t) =>
      (t.escalations || []).forEach((e: Escalation) => {
        if (e.team?.name) set.add(e.team.name);
      })
    );
    return Array.from(set);
  }, [tickets]);

  if (ticketsLoading || ratingsLoading) return <LoadingSkeleton />;

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-foreground text-2xl font-bold">Analytics &amp; Operations</h1>
          <p className="text-muted-foreground text-sm">Insights, trends, and ticket management tools</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Select
            value={dateFilter}
            onValueChange={(v) => {
              setParams(v !== "custom" ? { dateFilter: v, dateFrom: "", dateTo: "" } : { dateFilter: v });
              setCurrentPage(1);
            }}
          >
            <SelectTrigger className="w-[160px]" data-testid="health-date-filter">
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
                value={params.dateFrom}
                onChange={(e) => {
                  setParams({ dateFrom: e.target.value });
                  setCurrentPage(1);
                }}
                className="w-[150px]"
              />
              <Input
                type="date"
                value={params.dateTo}
                onChange={(e) => {
                  setParams({ dateTo: e.target.value });
                  setCurrentPage(1);
                }}
                className="w-[150px]"
              />
            </>
          )}
          {dateFilter === "custom" && !isDateRangeValid && <span className="text-destructive text-xs font-medium">Invalid range</span>}
        </div>
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

        <TabsContent value={activeTab} className="space-y-8">
          {activeTab === "tickets" && (
            <>
              <div className="bg-card rounded-lg border p-4">
                <h2 className="text-foreground mb-2 text-center font-semibold">Tickets Timeline</h2>
                <div style={{ width: "100%", height: 300 }}>
                  <ResponsiveContainer width="100%" height="100%">
                    <LineChart data={timelineData}>
                      <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                      <XAxis dataKey="date" stroke="var(--border)" tick={{ fill: "var(--muted-foreground)", fontSize: 11 }} />
                      <YAxis stroke="var(--border)" tick={{ fill: "var(--muted-foreground)", fontSize: 11 }} />
                      <Tooltip
                        contentStyle={{
                          background: "var(--popover)",
                          color: "var(--popover-foreground)",
                          border: "1px solid var(--border)",
                          borderRadius: "8px",
                          boxShadow: "0 4px 12px rgba(0,0,0,0.15)",
                        }}
                        labelStyle={{ color: "var(--popover-foreground)" }}
                        itemStyle={{ color: "var(--popover-foreground)" }}
                        cursor={{ stroke: "var(--border)", fill: "var(--accent)" }}
                      />
                      <Legend />
                      <Line type="monotone" dataKey="opened" stroke="var(--chart-1)" activeDot={{ r: 6 }} />
                      <Line type="monotone" dataKey="closed" stroke="var(--chart-2)" />
                      <Line type="monotone" dataKey="escalated" stroke="var(--chart-3)" />
                    </LineChart>
                  </ResponsiveContainer>
                </div>
              </div>

              <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">
                <div className="bg-card rounded-xl border p-5">
                  <p className="text-muted-foreground text-sm font-medium">Average Rating</p>
                  <div className="mt-2 flex items-baseline gap-2">
                    <span className="text-warning font-mono text-2xl font-semibold tracking-tight tabular-nums">
                      {avgRating.toFixed(1)}
                    </span>
                    <StarRating rating={avgRating} hideValue />
                    <span className="text-muted-foreground text-xs">
                      · {totalRatings} {totalRatings === 1 ? "rating" : "ratings"}
                    </span>
                  </div>
                </div>
                <MetricCard title="Avg Resolution Time" value={formatSecs(avgResolutionTimeSecs)} />
                <MetricCard title="Longest Active Ticket" value={formatSecs(largestActiveTicketSecs)} />
                <MetricCard title="Stale Tickets" value={staleTicketsCount} color="orange" />
              </div>

              {isAssignmentEnabled && assignmentsByDay.length > 0 && (
                <div className="bg-card mt-4 rounded-lg border p-4">
                  <h2 className="text-foreground mb-2 text-center font-semibold">Average Ticket Assignments per Support Engineer</h2>
                  <p className="text-muted-foreground mb-3 text-center text-sm">Average number of tickets assigned per engineer by day</p>
                  <div style={{ width: "100%", height: 300 }}>
                    <ResponsiveContainer width="100%" height="100%">
                      <LineChart data={assignmentsByDay}>
                        <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                        <XAxis
                          dataKey="date"
                          tickFormatter={(date: string) => new Date(date).toLocaleDateString()}
                          stroke="var(--border)"
                          tick={{ fill: "var(--muted-foreground)", fontSize: 11 }}
                        />
                        <YAxis label={{ value: "Avg Tickets/Engineer", angle: -90, position: "insideLeft" }} allowDecimals={true} />
                        <Tooltip
                          labelFormatter={(label) => new Date(label as string).toLocaleDateString()}
                          contentStyle={{
                            background: "var(--popover)",
                            color: "var(--popover-foreground)",
                            border: "1px solid var(--border)",
                            borderRadius: "8px",
                            boxShadow: "0 4px 12px rgba(0,0,0,0.15)",
                          }}
                          labelStyle={{ color: "var(--popover-foreground)" }}
                          itemStyle={{ color: "var(--popover-foreground)" }}
                          cursor={{ stroke: "var(--border)", fill: "var(--accent)" }}
                          formatter={(value: number, name: string) => {
                            if (name === "avgAssignments") return [value.toFixed(2), "Avg per Engineer"];
                            if (name === "totalAssignments") return [value, "Total Assigned"];
                            return [value, name];
                          }}
                        />
                        <Legend
                          formatter={(value: string) => {
                            if (value === "avgAssignments") return "Avg per Engineer";
                            if (value === "totalAssignments") return "Total Assigned";
                            return value;
                          }}
                        />
                        <Line type="monotone" dataKey="avgAssignments" stroke="var(--chart-4)" strokeWidth={2} activeDot={{ r: 6 }} />
                        <Line type="monotone" dataKey="totalAssignments" stroke="var(--chart-4)" strokeWidth={2} strokeDasharray="5 5" />
                      </LineChart>
                    </ResponsiveContainer>
                  </div>
                </div>
              )}

              {/* Tickets Opened by Requesting Team */}
              {ticketsByTeam.length > 0 && (
                <div className="bg-card mt-4 rounded-lg border p-4">
                  <h2 className="text-foreground mb-2 text-center font-semibold">Tickets Opened by Requesting Team (Top 10)</h2>
                  <div style={{ width: "100%", height: 300 }}>
                    <ResponsiveContainer width="100%" height="100%">
                      <BarChart data={ticketsByTeam}>
                        <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                        <XAxis
                          dataKey="name"
                          angle={-45}
                          textAnchor="end"
                          height={100}
                          stroke="var(--border)"
                          tick={{ fill: "var(--muted-foreground)", fontSize: 11 }}
                        />
                        <YAxis stroke="var(--border)" tick={{ fill: "var(--muted-foreground)", fontSize: 11 }} />
                        <Tooltip
                          contentStyle={{
                            background: "var(--popover)",
                            color: "var(--popover-foreground)",
                            border: "1px solid var(--border)",
                            borderRadius: "8px",
                            boxShadow: "0 4px 12px rgba(0,0,0,0.15)",
                          }}
                          labelStyle={{ color: "var(--popover-foreground)" }}
                          itemStyle={{ color: "var(--popover-foreground)" }}
                          cursor={{ stroke: "var(--border)", fill: "var(--accent)" }}
                        />
                        <Bar dataKey="value" fill="var(--chart-1)">
                          {ticketsByTeam.map((_, idx) => (
                            <Cell key={idx} fill={COLORS[idx % COLORS.length]} />
                          ))}
                        </Bar>
                      </BarChart>
                    </ResponsiveContainer>
                  </div>
                </div>
              )}

              {/* Current Active Tickets per Engineer */}
              {isAssignmentEnabled && activeTicketsPerEngineer.length > 0 && (
                <div className="bg-card mt-4 rounded-lg border p-4">
                  <h2 className="text-foreground mb-2 text-center font-semibold">Current Active Tickets per Engineer</h2>
                  <div style={{ width: "100%", height: 300 }}>
                    <ResponsiveContainer width="100%" height="100%">
                      <BarChart data={activeTicketsPerEngineer}>
                        <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                        <XAxis
                          dataKey="name"
                          angle={-45}
                          textAnchor="end"
                          height={100}
                          stroke="var(--border)"
                          tick={{ fill: "var(--muted-foreground)", fontSize: 11 }}
                        />
                        <YAxis allowDecimals={false} stroke="var(--border)" tick={{ fill: "var(--muted-foreground)", fontSize: 11 }} />
                        <Tooltip
                          contentStyle={{
                            background: "var(--popover)",
                            color: "var(--popover-foreground)",
                            border: "1px solid var(--border)",
                            borderRadius: "8px",
                            boxShadow: "0 4px 12px rgba(0,0,0,0.15)",
                          }}
                          labelStyle={{ color: "var(--popover-foreground)" }}
                          itemStyle={{ color: "var(--popover-foreground)" }}
                          cursor={{ stroke: "var(--border)", fill: "var(--accent)" }}
                        />
                        <Bar dataKey="tickets" fill="var(--chart-4)">
                          {activeTicketsPerEngineer.map((_, idx) => (
                            <Cell key={idx} fill={COLORS[idx % COLORS.length]} />
                          ))}
                        </Bar>
                      </BarChart>
                    </ResponsiveContainer>
                  </div>
                </div>
              )}

              {/* Tickets Opened by Hour of Day & Busiest Periods Heatmap - Side by Side */}
              <div className="mt-4 grid grid-cols-1 gap-6 lg:grid-cols-2">
                {/* Tickets Opened by Hour of Day */}
                {ticketsByHour.length > 0 && (
                  <div className="bg-card rounded-lg border p-4">
                    <h2 className="text-foreground mb-2 text-center font-semibold">Tickets Opened by Hour of Day</h2>
                    <p className="text-muted-foreground mb-3 text-center text-sm">Consolidation of all tickets by their opened time</p>
                    <div style={{ width: "100%", height: 300 }}>
                      <ResponsiveContainer width="100%" height="100%">
                        <BarChart data={ticketsByHour}>
                          <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                          <XAxis
                            dataKey="hourLabel"
                            angle={-45}
                            textAnchor="end"
                            height={80}
                            stroke="var(--border)"
                            tick={{ fill: "var(--muted-foreground)", fontSize: 11 }}
                          />
                          <YAxis allowDecimals={false} stroke="var(--border)" tick={{ fill: "var(--muted-foreground)", fontSize: 11 }} />
                          <Tooltip
                            contentStyle={{
                              background: "var(--popover)",
                              color: "var(--popover-foreground)",
                              border: "1px solid var(--border)",
                              borderRadius: "8px",
                              boxShadow: "0 4px 12px rgba(0,0,0,0.15)",
                            }}
                            labelStyle={{ color: "var(--popover-foreground)" }}
                            itemStyle={{ color: "var(--popover-foreground)" }}
                            cursor={{ stroke: "var(--border)", fill: "var(--accent)" }}
                          />
                          <Bar dataKey="count" fill="var(--chart-2)" />
                        </BarChart>
                      </ResponsiveContainer>
                    </div>
                  </div>
                )}

                {/* Busiest Periods Heatmap */}
                {busiestPeriods.length > 0 && (
                  <div className="bg-card rounded-lg border p-4">
                    <h2 className="text-foreground mb-2 text-center font-semibold">Busiest Periods Heatmap</h2>
                    <p className="text-muted-foreground mb-3 text-center text-xs">Weekdays (Mon-Fri)</p>
                    <div className="overflow-x-auto">
                      <table className="w-full text-xs">
                        <thead>
                          <tr>
                            <th className="bg-card sticky left-0 z-10 p-2 text-left">Day</th>
                            {Array.from({ length: 12 }, (_, idx) => {
                              const hour = idx + 7; // Hours 7-18
                              return (
                                <th key={hour} className="min-w-[30px] p-1 text-center">
                                  {String(hour).padStart(2, "0")}
                                </th>
                              );
                            })}
                          </tr>
                        </thead>
                        <tbody>
                          {busiestPeriods.map((row, idx) => {
                            const hourValues = Array.from({ length: 12 }, (_, idx) => row[idx + 7] as number);
                            const maxValue = Math.max(...hourValues, 0);
                            return (
                              <tr key={idx} className="border-t">
                                <td className="bg-card sticky left-0 p-2 font-medium">{row.day}</td>
                                {Array.from({ length: 12 }, (_, idx) => {
                                  const hour = idx + 7; // Hours 7-18
                                  const value = row[hour] as number;
                                  const intensity = maxValue > 0 ? (value / maxValue) * 100 : 0;
                                  const bgColor =
                                    intensity > 70
                                      ? "bg-destructive"
                                      : intensity > 40
                                        ? "bg-warning"
                                        : intensity > 20
                                          ? "bg-warning/30"
                                          : "bg-success/10";
                                  return (
                                    <td
                                      key={hour}
                                      className={`p-1 text-center ${bgColor} text-foreground font-medium`}
                                      title={`${row.day} ${String(hour).padStart(2, "0")}:00 - ${value} tickets`}
                                    >
                                      {value > 0 ? value : ""}
                                    </td>
                                  );
                                })}
                              </tr>
                            );
                          })}
                        </tbody>
                      </table>
                    </div>
                  </div>
                )}
              </div>

              {/* Capacity Planning - Combined Section */}
              {capacityVsDemand && (
                <div className="bg-card mt-4 overflow-hidden rounded-xl border">
                  {/* Capacity vs Demand - Always Visible */}
                  <div className="border-b p-5">
                    <div className="mb-4 flex items-start justify-between gap-4">
                      <div>
                        <h2 className="text-foreground text-base font-semibold">Capacity vs Demand</h2>
                        <p className="text-muted-foreground text-xs">Estimated engineer load for the selected window</p>
                      </div>
                    </div>

                    {/* Inputs row */}
                    <div className="mb-5 grid grid-cols-1 gap-4 sm:grid-cols-2">
                      <div className="space-y-1.5">
                        <label htmlFor="engineers-on-rota" className="text-foreground text-sm font-medium">
                          Engineers on Rota
                          {capacityVsDemand.totalEngineers > 0 && (
                            <span className="text-muted-foreground ml-2 text-xs font-normal">
                              of {capacityVsDemand.totalEngineers} total
                            </span>
                          )}
                        </label>
                        <Input
                          id="engineers-on-rota"
                          type="number"
                          min={1}
                          max={capacityVsDemand.totalEngineers || 10}
                          value={engineersOnRota}
                          onChange={(e) => {
                            const value = parseInt(e.target.value) || 1;
                            setEngineersOnRota(Math.max(1, Math.min(value, capacityVsDemand.totalEngineers || 10)));
                          }}
                          className="w-28"
                        />
                      </div>
                      <div className="space-y-1.5">
                        <label htmlFor="tickets-per-engineer" className="text-foreground text-sm font-medium">
                          Tickets per Engineer Capacity
                        </label>
                        <Input
                          id="tickets-per-engineer"
                          type="number"
                          min={1}
                          max={50}
                          value={ticketsPerEngineerCapacity}
                          onChange={(e) => {
                            const value = parseInt(e.target.value) || 1;
                            setTicketsPerEngineerCapacity(Math.max(1, Math.min(value, 50)));
                          }}
                          className="w-28"
                        />
                      </div>
                    </div>

                    {/* Stat tiles row */}
                    <div className="mb-5 grid grid-cols-1 gap-3 sm:grid-cols-3">
                      <div className="bg-muted/40 rounded-lg border p-3">
                        <p className="text-muted-foreground text-xs font-medium">Total Capacity</p>
                        <p className="text-foreground mt-1 font-mono text-xl font-semibold tracking-tight tabular-nums">
                          {capacityVsDemand.totalCapacity}
                          <span className="text-muted-foreground ml-1 text-xs font-normal">tickets</span>
                        </p>
                      </div>
                      <div className="bg-muted/40 rounded-lg border p-3">
                        <p className="text-muted-foreground text-xs font-medium">Open Tickets</p>
                        <p className="text-foreground mt-1 font-mono text-xl font-semibold tracking-tight tabular-nums">
                          {capacityVsDemand.openTickets}
                        </p>
                      </div>
                      <div className="bg-muted/40 rounded-lg border p-3">
                        <p className="text-muted-foreground text-xs font-medium">Tickets per Engineer</p>
                        <p className="text-foreground mt-1 font-mono text-xl font-semibold tracking-tight tabular-nums">
                          {capacityVsDemand.ticketsPerEngineer}
                        </p>
                      </div>
                    </div>

                    {/* Utilization bar */}
                    <div>
                      <div className="mb-2 flex items-center justify-between">
                        <span className="text-foreground text-sm font-medium">Capacity Utilization</span>
                        <span
                          className={`font-mono text-sm font-semibold tabular-nums ${
                            capacityVsDemand.capacityUtilization > 80
                              ? "text-destructive"
                              : capacityVsDemand.capacityUtilization > 60
                                ? "text-warning"
                                : "text-success"
                          }`}
                        >
                          {capacityVsDemand.capacityUtilization}%
                          {capacityVsDemand.capacityUtilization > 100 && <span className="ml-2 text-xs font-medium">Over capacity</span>}
                        </span>
                      </div>
                      <div className="bg-muted h-2 w-full overflow-hidden rounded-full">
                        <div
                          className={`h-2 rounded-full transition-all duration-500 ${
                            capacityVsDemand.capacityUtilization > 80
                              ? "bg-destructive"
                              : capacityVsDemand.capacityUtilization > 60
                                ? "bg-warning"
                                : "bg-success"
                          }`}
                          style={{ width: `${Math.min(100, capacityVsDemand.capacityUtilization)}%` }}
                        />
                      </div>
                      {capacityVsDemand.capacityUtilization > 100 && (
                        <p className="text-destructive mt-2 text-xs">Over capacity by {capacityVsDemand.capacityUtilization - 100}%</p>
                      )}
                    </div>
                  </div>

                  {/* Capacity Insights by Time Block - Collapsible */}
                  {capacityInsights.length > 0 && (
                    <>
                      <button
                        onClick={() => setCapacityInsightsExpanded(!capacityInsightsExpanded)}
                        className="hover:bg-muted/40 flex w-full items-center justify-between p-3 transition-colors"
                      >
                        <div className="flex items-center gap-2">
                          <Headphones className="text-foreground h-4 w-4" />
                          <h3 className="text-foreground text-sm font-semibold">Capacity Insights by Time Block</h3>
                          <span className="text-muted-foreground text-xs font-normal">
                            ({capacityInsightsExpanded ? "Click to collapse" : "Click to expand"})
                          </span>
                        </div>
                        <ChevronDown
                          className={`text-foreground h-4 w-4 transition-transform duration-200 ${capacityInsightsExpanded ? "rotate-180" : ""}`}
                        />
                      </button>

                      {capacityInsightsExpanded && (
                        <div className="border-t px-3 pt-3 pb-3">
                          <p className="text-muted-foreground mb-3 text-center text-xs">
                            Total tickets per 2-hour block (local time) | Capacity: {engineersOnRota} engineers ×{" "}
                            {ticketsPerEngineerCapacity} = {engineersOnRota * ticketsPerEngineerCapacity}
                          </p>
                          <div className="grid grid-cols-1 gap-3 md:grid-cols-2 lg:grid-cols-3">
                            {capacityInsights.map((insight, idx) => {
                              const statusBg =
                                insight.status === "over"
                                  ? "bg-destructive/10 border-destructive/30"
                                  : insight.status === "near"
                                    ? "bg-warning/10 border-warning/30"
                                    : "bg-success/10 border-success/30";
                              const utilizationColor =
                                insight.utilization > 100
                                  ? "text-destructive font-semibold"
                                  : insight.utilization > 80
                                    ? "text-warning"
                                    : "text-success";

                              return (
                                <div key={idx} className={`rounded-lg border p-3 ${statusBg}`}>
                                  <div className="text-foreground mb-2 text-sm font-semibold">{insight.timeBlock}</div>
                                  <div className="space-y-1 text-xs">
                                    <div className="flex justify-between">
                                      <span className="text-muted-foreground">Total Tickets:</span>
                                      <span className="font-medium">{insight.totalTickets}</span>
                                    </div>
                                    <div className="flex justify-between">
                                      <span className="text-muted-foreground">Avg per weekday:</span>
                                      <span className="font-medium">{insight.avgPerWeekday.toFixed(1)}</span>
                                    </div>
                                    <div className="flex justify-between">
                                      <span className="text-muted-foreground">Capacity:</span>
                                      <span>{insight.currentCapacity}</span>
                                    </div>
                                    <div className="flex justify-between">
                                      <span className="text-muted-foreground">Utilization:</span>
                                      <span className={`font-semibold ${utilizationColor}`}>{insight.utilization}%</span>
                                    </div>
                                    <div className="flex justify-between border-t pt-1">
                                      <span className="text-muted-foreground">Recommended:</span>
                                      <span className="text-foreground font-semibold">{insight.recommendedRange} engineers</span>
                                    </div>
                                  </div>
                                </div>
                              );
                            })}
                          </div>
                          <div className="mt-3 border-t pt-3">
                            <div className="flex flex-wrap justify-center gap-3 text-xs">
                              <div className="flex items-center gap-1">
                                <div className="bg-destructive/10 border-destructive/30 h-3 w-3 rounded border"></div>
                                <span>Over Capacity (&gt;100%)</span>
                              </div>
                              <div className="flex items-center gap-1">
                                <div className="bg-warning/10 border-warning/30 h-3 w-3 rounded border"></div>
                                <span>Near Capacity (80-100%)</span>
                              </div>
                              <div className="flex items-center gap-1">
                                <div className="bg-success/10 border-success/30 h-3 w-3 rounded border"></div>
                                <span>Under Capacity (&lt;80%)</span>
                              </div>
                            </div>
                          </div>
                        </div>
                      )}
                    </>
                  )}
                </div>
              )}
            </>
          )}

          {activeTab === "ratings" && (
            <div className="space-y-6">
              <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
                <div className="bg-card rounded-xl border p-5">
                  <p className="text-muted-foreground text-sm font-medium">Consolidated Average Rating</p>
                  <div className="mt-2 flex items-baseline gap-2">
                    <span className="text-warning font-mono text-2xl font-semibold tracking-tight tabular-nums">
                      {avgRating.toFixed(1)}
                    </span>
                    <StarRating rating={avgRating} hideValue />
                    <span className="text-muted-foreground text-xs">
                      · {totalRatings} {totalRatings === 1 ? "rating" : "ratings"}
                    </span>
                  </div>
                </div>
                <MetricCard title="Percentage Rated" value={`${percentageRated}%`} color="green" />
                <MetricCard title="Ratings Count" value={totalRatings} color="green" />
              </div>
              <div className="space-y-6">
                <div className="bg-card rounded-lg border p-4">
                  <h2 className="text-foreground mb-2 text-center text-xl font-semibold">Ratings Received per Week</h2>
                  <div style={{ width: "100%", height: 320 }}>
                    <ResponsiveContainer width="100%" height="100%">
                      <BarChart data={ratingsByWeek}>
                        <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                        <XAxis
                          dataKey="weekStart"
                          tickFormatter={(d: string) => new Date(d).toLocaleDateString()}
                          stroke="var(--border)"
                          tick={{ fill: "var(--muted-foreground)", fontSize: 11 }}
                        />
                        <YAxis allowDecimals={false} stroke="var(--border)" tick={{ fill: "var(--muted-foreground)", fontSize: 11 }} />
                        <Tooltip
                          labelFormatter={(label) => new Date(label as string).toLocaleDateString()}
                          contentStyle={{
                            background: "var(--popover)",
                            color: "var(--popover-foreground)",
                            border: "1px solid var(--border)",
                            borderRadius: "8px",
                            boxShadow: "0 4px 12px rgba(0,0,0,0.15)",
                          }}
                          labelStyle={{ color: "var(--popover-foreground)" }}
                          itemStyle={{ color: "var(--popover-foreground)" }}
                          cursor={{ stroke: "var(--border)", fill: "var(--accent)" }}
                        />
                        <Legend />
                        <Bar dataKey="count" name="Ratings" fill="var(--chart-2)">
                          {ratingsByWeek.map((_, idx) => (
                            <Cell key={idx} fill={COLORS[idx % COLORS.length]} />
                          ))}
                        </Bar>
                      </BarChart>
                    </ResponsiveContainer>
                  </div>
                </div>

                <div className="bg-card rounded-lg border p-4">
                  <h2 className="text-foreground mb-2 text-center text-xl font-semibold">Average Rating per Week</h2>
                  <div style={{ width: "100%", height: 320 }}>
                    <ResponsiveContainer width="100%" height="100%">
                      <LineChart data={avgRatingsByWeek}>
                        <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                        <XAxis
                          dataKey="weekStart"
                          tickFormatter={(d: string) => new Date(d).toLocaleDateString()}
                          stroke="var(--border)"
                          tick={{ fill: "var(--muted-foreground)", fontSize: 11 }}
                        />
                        <YAxis
                          domain={[0, 5]}
                          tickCount={6}
                          stroke="var(--border)"
                          tick={{ fill: "var(--muted-foreground)", fontSize: 11 }}
                        />
                        <Tooltip
                          labelFormatter={(label) => new Date(label as string).toLocaleDateString()}
                          contentStyle={{
                            background: "var(--popover)",
                            color: "var(--popover-foreground)",
                            border: "1px solid var(--border)",
                            borderRadius: "8px",
                            boxShadow: "0 4px 12px rgba(0,0,0,0.15)",
                          }}
                          labelStyle={{ color: "var(--popover-foreground)" }}
                          itemStyle={{ color: "var(--popover-foreground)" }}
                          cursor={{ stroke: "var(--border)", fill: "var(--accent)" }}
                        />
                        <Legend />
                        <Line type="monotone" dataKey="average" name="Average Rating" stroke="var(--chart-3)" dot />
                      </LineChart>
                    </ResponsiveContainer>
                  </div>
                </div>
              </div>
            </div>
          )}

          {activeTab === "workbench" && (
            <>
              {/* Bulk Reassign Section - Only show if assignment is enabled */}
              {isAssignmentEnabled && (
                <div className="bg-card mb-4 overflow-hidden rounded-xl border">
                  <button
                    onClick={() => setBulkReassignExpanded(!bulkReassignExpanded)}
                    className="hover:bg-muted/40 flex w-full cursor-pointer items-center justify-between px-6 py-4 transition-colors"
                  >
                    <div className="flex items-center gap-3">
                      <h3 className="text-foreground text-base font-semibold">Bulk Reassign Tickets</h3>
                    </div>
                    <ChevronDown
                      className={`text-muted-foreground h-4 w-4 transition-transform duration-[200ms] ${bulkReassignExpanded ? "" : "-rotate-90"}`}
                    />
                  </button>

                  {bulkReassignExpanded && (
                    <div className="animate-in fade-in slide-in-from-top-1 space-y-3 px-6 pt-2 pb-6 duration-[200ms]">
                      <div className="flex flex-wrap items-end gap-3">
                        <div className="flex flex-col gap-1.5">
                          <Label htmlFor="bulk-from" className="text-foreground text-sm font-medium">
                            From
                          </Label>
                          <Select value={bulkReassignFrom || "__none"} onValueChange={(v) => setBulkReassignFrom(v === "__none" ? "" : v)}>
                            <SelectTrigger id="bulk-from" className="w-[220px]">
                              <SelectValue placeholder="Select assignee..." />
                            </SelectTrigger>
                            <SelectContent>
                              <SelectItem value="__none">Select assignee...</SelectItem>
                              <SelectItem value="unassigned">Unassigned</SelectItem>
                              {supportMembers?.map((member: SupportMember) => (
                                <SelectItem key={member.userId} value={member.displayName}>
                                  {member.displayName}
                                </SelectItem>
                              ))}
                            </SelectContent>
                          </Select>
                        </div>
                        <div className="flex flex-col gap-1.5">
                          <Label htmlFor="bulk-to" className="text-foreground text-sm font-medium">
                            To
                          </Label>
                          <Select value={bulkReassignTo || "__none"} onValueChange={(v) => setBulkReassignTo(v === "__none" ? "" : v)}>
                            <SelectTrigger id="bulk-to" className="w-[220px]">
                              <SelectValue placeholder="Select assignee..." />
                            </SelectTrigger>
                            <SelectContent>
                              <SelectItem value="__none">Select assignee...</SelectItem>
                              {supportMembers?.map((member: SupportMember) => (
                                <SelectItem key={member.userId} value={member.displayName}>
                                  {member.displayName}
                                </SelectItem>
                              ))}
                            </SelectContent>
                          </Select>
                        </div>
                        <Button
                          onClick={() => {
                            if (bulkReassignFrom && bulkReassignTo) {
                              if (bulkReassignFrom === bulkReassignTo) {
                                setReassignMessage({
                                  type: "error",
                                  text: "Source and target assignee cannot be the same. Please select different assignees.",
                                });
                                setTimeout(() => setReassignMessage(null), 5000);
                                return;
                              }

                              const affectedTickets = filteredTickets.filter(
                                (t) =>
                                  t.status?.toLowerCase() === "opened" &&
                                  (bulkReassignFrom === "unassigned" ? !t.assignedTo : t.assignedTo === bulkReassignFrom)
                              );
                              const count = affectedTickets.length;
                              if (count === 0) {
                                setReassignMessage({ type: "error", text: "No tickets found matching the selected assignee." });
                                setTimeout(() => setReassignMessage(null), 5000);
                                return;
                              }

                              setConfirmationDetails({
                                from: bulkReassignFrom,
                                to: bulkReassignTo,
                                count: count,
                                tickets: affectedTickets,
                              });
                              setShowConfirmation(true);
                              setReassignMessage(null);
                            }
                          }}
                          disabled={!bulkReassignFrom || !bulkReassignTo || isReassigning || showConfirmation}
                        >
                          Reassign All
                        </Button>
                        {bulkReassignFrom && (
                          <span className="text-muted-foreground inline-flex items-center rounded-full border px-2 py-0.5 text-xs tabular-nums">
                            {
                              filteredTickets.filter(
                                (t) =>
                                  t.status?.toLowerCase() === "opened" &&
                                  (bulkReassignFrom === "unassigned" ? !t.assignedTo : t.assignedTo === bulkReassignFrom)
                              ).length
                            }{" "}
                            open ticket
                            {filteredTickets.filter(
                              (t) =>
                                t.status?.toLowerCase() === "opened" &&
                                (bulkReassignFrom === "unassigned" ? !t.assignedTo : t.assignedTo === bulkReassignFrom)
                            ).length === 1
                              ? ""
                              : "s"}
                          </span>
                        )}
                      </div>
                      <p className="text-muted-foreground text-xs">Only open tickets will be reassigned.</p>

                      {/* Success/Error Messages - Inside bulk reassign section */}
                      {reassignMessage && (
                        <div
                          className={`mt-3 rounded-md border p-3 text-sm ${
                            reassignMessage.type === "success"
                              ? "bg-success/10 border-success/30 text-success"
                              : "bg-destructive/10 border-destructive/30 text-destructive"
                          }`}
                        >
                          <p className="font-medium">{reassignMessage.text}</p>
                        </div>
                      )}

                      {/* Inline Confirmation - Inside bulk reassign section */}
                      {showConfirmation && confirmationDetails && (
                        <div className="bg-warning/10 border-warning/30 mt-3 rounded-md border p-3">
                          <div className="flex items-start gap-2">
                            <AlertTriangle className="text-warning mt-0.5 h-4 w-4 flex-shrink-0" />
                            <div className="flex-1">
                              <h4 className="text-foreground mb-1 text-sm font-semibold">Confirm Bulk Reassignment</h4>
                              <p className="text-foreground mb-2 text-xs">
                                Hand over{" "}
                                <strong>
                                  {confirmationDetails.count} ticket{confirmationDetails.count === 1 ? "" : "s"}
                                </strong>{" "}
                                from <strong>{confirmationDetails.from === "unassigned" ? "Unassigned" : confirmationDetails.from}</strong>{" "}
                                to <strong>{confirmationDetails.to}</strong>
                              </p>
                              <div className="flex gap-2">
                                <button
                                  onClick={async () => {
                                    setShowConfirmation(false);
                                    setIsReassigning(true);
                                    setReassignMessage(null);

                                    try {
                                      const ticketIds = confirmationDetails.tickets.map((t) => t.id);
                                      const targetUserId =
                                        supportMembers?.find((m) => m.displayName === confirmationDetails.to)?.userId || "";

                                      if (!targetUserId) {
                                        throw new Error("Could not find user ID for selected assignee");
                                      }

                                      const request: BulkReassignRequest = {
                                        ticketIds,
                                        assignedTo: targetUserId,
                                      };

                                      const response = await fetch("/api/assignment/bulk-reassign", {
                                        method: "POST",
                                        headers: {
                                          "Content-Type": "application/json",
                                        },
                                        body: JSON.stringify(request),
                                      });

                                      if (!response.ok) {
                                        throw new Error(`Failed to bulk reassign: ${response.status}`);
                                      }

                                      const result: BulkReassignResult = await response.json();

                                      setReassignMessage({
                                        type: "success",
                                        text: `${result.message} (${result.successCount} ticket${result.successCount === 1 ? "" : "s"})`,
                                      });

                                      setBulkReassignFrom("");
                                      setBulkReassignTo("");
                                      setConfirmationDetails(null);

                                      await queryClient.invalidateQueries({ queryKey: ["tickets"] });
                                    } catch (error) {
                                      console.error("Bulk reassign failed:", error);
                                      setReassignMessage({
                                        type: "error",
                                        text: `Failed to reassign tickets: ${error instanceof Error ? error.message : "Unknown error"}`,
                                      });
                                    } finally {
                                      setIsReassigning(false);
                                      setTimeout(() => setReassignMessage(null), 10000);
                                    }
                                  }}
                                  disabled={isReassigning}
                                  className="bg-warning hover:bg-warning flex items-center gap-1.5 rounded px-3 py-1.5 text-xs font-medium text-white transition-colors disabled:cursor-not-allowed disabled:opacity-50"
                                >
                                  {isReassigning ? (
                                    <>
                                      <svg
                                        className="h-3 w-3 animate-spin"
                                        xmlns="http://www.w3.org/2000/svg"
                                        fill="none"
                                        viewBox="0 0 24 24"
                                      >
                                        <circle
                                          className="opacity-25"
                                          cx="12"
                                          cy="12"
                                          r="10"
                                          stroke="currentColor"
                                          strokeWidth="4"
                                        ></circle>
                                        <path
                                          className="opacity-75"
                                          fill="currentColor"
                                          d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
                                        ></path>
                                      </svg>
                                      Reassigning...
                                    </>
                                  ) : (
                                    "Yes, Reassign"
                                  )}
                                </button>
                                <button
                                  onClick={() => {
                                    setShowConfirmation(false);
                                    setConfirmationDetails(null);
                                  }}
                                  disabled={isReassigning}
                                  className="bg-muted text-foreground hover:bg-muted rounded px-3 py-1.5 text-xs font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-50"
                                >
                                  Cancel
                                </button>
                              </div>
                            </div>
                          </div>
                        </div>
                      )}
                    </div>
                  )}
                </div>
              )}

              {/* Filters — faceted single-value */}
              <div className="mb-4 flex flex-wrap items-center gap-2">
                <SingleSelectFilter
                  title="Requesting Team"
                  value={inquiringTeamFilter || undefined}
                  onChange={(v) => {
                    setInquiringTeamFilter(v ?? "");
                    setCurrentPage(1);
                  }}
                  options={inquiringTeamsWithTickets.map((team) => ({ label: team, value: team }))}
                />
                <SingleSelectFilter
                  title="Escalated To"
                  value={escalatedTeamFilter || undefined}
                  onChange={(v) => {
                    setEscalatedTeamFilter(v ?? "");
                    setCurrentPage(1);
                  }}
                  options={escalatedTeamsWithTickets.map((team) => ({ label: team, value: team }))}
                />
                <SingleSelectFilter
                  title="Status"
                  value={statusFilter || undefined}
                  onChange={(v) => {
                    setStatusFilter(v ?? "");
                    setCurrentPage(1);
                  }}
                  showSearch={false}
                  options={[
                    { label: "Opened", value: "opened" },
                    { label: "Closed", value: "closed" },
                    { label: "Stale", value: "stale" },
                  ]}
                />
                {isAssignmentEnabled && (
                  <SingleSelectFilter
                    title="Assignee"
                    value={assigneeFilter || undefined}
                    onChange={(v) => {
                      setAssigneeFilter(v ?? "");
                      setCurrentPage(1);
                    }}
                    options={[
                      { label: "Unassigned", value: "unassigned" },
                      ...(supportMembers ?? []).map((member: SupportMember) => ({
                        label: member.displayName,
                        value: member.displayName,
                      })),
                    ]}
                  />
                )}
                <SingleSelectFilter
                  title="Rated"
                  value={ratedFilter || undefined}
                  onChange={(v) => {
                    setRatedFilter(v ?? "");
                    setCurrentPage(1);
                  }}
                  showSearch={false}
                  options={[
                    { label: "Yes", value: "yes" },
                    { label: "No", value: "no" },
                  ]}
                />
              </div>

              {/* Tickets Table */}
              <div className="mb-6 overflow-hidden rounded-lg border">
                <Table>
                  <TableHeader className="bg-muted z-10">
                    <TableRow>
                      <TableHead>Team</TableHead>
                      <TableHead>Impact</TableHead>
                      {isAssignmentEnabled && <TableHead>Support Engineer</TableHead>}
                      <TableHead>Escalated To</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead>Opened At</TableHead>
                      <TableHead>Rated</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {paginatedTickets.length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={isAssignmentEnabled ? 7 : 6} className="text-muted-foreground py-8 text-center">
                          <div className="flex flex-col items-center gap-2">
                            <AlertTriangle className="h-6 w-6" />
                            <p className="font-medium">No tickets found</p>
                            <p className="text-sm">Try adjusting your filters</p>
                          </div>
                        </TableCell>
                      </TableRow>
                    ) : (
                      paginatedTickets.map((t) => {
                        const { opened } = getOpenedClosed(t);
                        return (
                          <TableRow key={t.id}>
                            <TableCell>{t.team?.name || "-"}</TableCell>
                            <TableCell>
                              {registryData?.impacts.find((i: TicketImpact) => i.code === t.impact)?.label || t.impact || "-"}
                            </TableCell>
                            {isAssignmentEnabled && (
                              <TableCell>
                                {t.assignedTo ? (
                                  <span className="text-foreground">{t.assignedTo}</span>
                                ) : (
                                  <span className="text-muted-foreground">Unassigned</span>
                                )}
                              </TableCell>
                            )}
                            <TableCell>
                              {(t.escalations || []).length ? (
                                (t.escalations || [])
                                  .map((e: Escalation) => e.team?.name)
                                  .filter(Boolean)
                                  .join(", ")
                              ) : (
                                <span className="text-muted-foreground">None</span>
                              )}
                            </TableCell>
                            <TableCell>
                              <span
                                className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-semibold ${statusColors[t.status?.toLowerCase()] || "bg-muted text-foreground"}`}
                              >
                                {t.status}
                              </span>
                            </TableCell>
                            <TableCell className="text-muted-foreground">{opened ? opened.toLocaleDateString() : "-"}</TableCell>
                            <TableCell>
                              {t.ratingSubmitted ? (
                                <span className="text-success font-medium">Yes</span>
                              ) : (
                                <span className="text-muted-foreground">No</span>
                              )}
                            </TableCell>
                          </TableRow>
                        );
                      })
                    )}
                  </TableBody>
                </Table>

                {(() => {
                  const totalPages = Math.max(1, Math.ceil(filteredTickets.length / ticketsPerPage));
                  if (totalPages <= 1) return null;

                  return (
                    <div className="flex items-center justify-end gap-4 border-t px-6 py-3">
                      <span className="text-muted-foreground text-sm">
                        Page {currentPage} of {totalPages}
                      </span>
                      <Button variant="outline" size="sm" disabled={currentPage === 1} onClick={() => setCurrentPage((p) => p - 1)}>
                        Previous
                      </Button>
                      <Button
                        variant="outline"
                        size="sm"
                        disabled={currentPage === totalPages}
                        onClick={() => setCurrentPage((p) => p + 1)}
                      >
                        Next
                      </Button>
                    </div>
                  );
                })()}
              </div>
            </>
          )}
        </TabsContent>
      </Tabs>
    </div>
  );

  function formatSecs(secs: number) {
    if (!secs || secs <= 0) return "-";
    if (secs < 60) return `${Math.round(secs)}s`;
    const m = Math.floor(secs / 60);
    const s = Math.floor(secs % 60);
    if (m < 60) return `${m}m ${s}s`;
    const h = Math.floor(m / 60);
    const mm = m % 60;
    return `${h}h ${mm}m`;
  }
}

// --- MetricCard & StarRating components ---
export function MetricCard({ title, value, extra, color }: { title: string; value: React.ReactNode; extra?: string; color?: string }) {
  const valueColor: Record<string, string> = {
    blue: "text-primary",
    green: "text-success",
    purple: "text-foreground",
    yellow: "text-warning",
    orange: "text-warning",
    red: "text-destructive",
  };
  return (
    <div className="bg-card rounded-xl border p-5">
      <p className="text-muted-foreground text-sm font-medium">{title}</p>
      <div
        className={`mt-2 font-mono text-2xl font-semibold tracking-tight tabular-nums ${color ? (valueColor[color] ?? "text-foreground") : "text-foreground"}`}
      >
        {value}
      </div>
      {extra && <p className="text-muted-foreground mt-1 text-xs">{extra}</p>}
    </div>
  );
}

function StarRating({ rating, hideValue = false }: { rating: number; hideValue?: boolean }) {
  const max = 5;
  const fullStars = Math.floor(rating);
  const halfStar = rating % 1 >= 0.5;
  return (
    <div className="inline-flex items-center gap-0.5">
      {Array.from({ length: max }, (_, i) => {
        if (i < fullStars) {
          return <Star key={i} className="fill-warning text-warning h-4 w-4" />;
        }
        if (i === fullStars && halfStar) {
          return <Star key={i} className="text-warning h-4 w-4" />;
        }
        return <Star key={i} className="text-muted-foreground/40 h-4 w-4" />;
      })}
      {!hideValue && <span className="text-muted-foreground ml-1 text-sm">{rating.toFixed(1)}</span>}
    </div>
  );
}
