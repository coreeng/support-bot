"use client";

import { hasActiveProductTags } from "@/components/products/products";
import EditTicketModal from "@/components/tickets/EditTicketModal";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { PRESET_DAYS } from "@/lib/dateRange";
import { useRegistry, useSummary } from "@/lib/hooks";
import { enumValidator, isoDateValidator, useUrlParams } from "@/lib/hooks/useUrlParams";
import type { SummaryCount, SummaryData, SummarySection, SummaryTicket } from "@/lib/types/summary";
import { useQueryClient } from "@tanstack/react-query";
import { AlertCircle, ChevronDown } from "lucide-react";
import { useMemo, useState, type ReactNode } from "react";

/** The presets this page offers; the default window is the last 2 weeks ending yesterday. */
const SUMMARY_PRESETS = ["lastWeek", "last2Weeks", "lastMonth", "custom"] as const;
type SummaryPreset = (typeof SUMMARY_PRESETS)[number];

const DEFAULT_PRESET: Exclude<SummaryPreset, "custom"> = "last2Weeks";

const PRESET_LABELS: Record<SummaryPreset, string> = {
  lastWeek: "Last week",
  last2Weeks: "Last 2 weeks",
  lastMonth: "Last month",
  custom: "Custom range",
};

const toDateString = (date: Date): string => date.toISOString().split("T")[0];

/**
 * A window of `days` inclusive days ending yesterday — today is unfinished, and excluding it
 * keeps the window (and so the cached summary) stable for the whole day.
 */
function windowEndingYesterday(days: number): { from: string; to: string } {
  const end = new Date();
  end.setDate(end.getDate() - 1);
  const start = new Date(end);
  start.setDate(end.getDate() - (days - 1));
  return { from: toDateString(start), to: toDateString(end) };
}

const ACCENTS = {
  primary: { bar: "bg-primary", track: "bg-primary/10", badge: "bg-primary/10 text-primary" },
  info: { bar: "bg-info", track: "bg-info/10", badge: "bg-info/10 text-info" },
  success: { bar: "bg-success", track: "bg-success/10", badge: "bg-success/10 text-success" },
  purple: { bar: "bg-chart-4", track: "bg-chart-4/10", badge: "bg-chart-4/10 text-chart-4" },
  warning: { bar: "bg-warning", track: "bg-warning/10", badge: "bg-warning/10 text-warning" },
} as const;

type Accent = keyof typeof ACCENTS;

/** Distinct colours for the ranked drivers, matching the stacked bar to its rows. */
const DRIVER_COLORS = [
  "bg-emerald-700",
  "bg-amber-600",
  "bg-teal-600",
  "bg-red-700",
  "bg-violet-500",
  "bg-sky-600",
  "bg-pink-600",
] as const;

const sumCounts = (counts: SummaryCount[]): number => counts.reduce((sum, count) => sum + count.count, 0);

const sharePercent = (count: number, total: number): number => (total > 0 ? Math.round((count / total) * 100) : 0);

function formatGeneratedAt(timestamp: string): string {
  const parsed = new Date(timestamp);
  if (isNaN(parsed.getTime())) return timestamp;
  return new Intl.DateTimeFormat("en-GB", {
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
    timeZone: "UTC",
  }).format(parsed);
}

/**
 * Formats an inclusive date window compactly, sharing whatever the two ends have in common:
 * "12 – 25 Aug 2026", "28 Jul – 25 Aug 2026", "28 Dec 2025 – 10 Jan 2026".
 */
function formatWindow(from: string, to: string): string {
  const start = new Date(`${from}T12:00:00Z`);
  const end = new Date(`${to}T12:00:00Z`);
  if (isNaN(start.getTime()) || isNaN(end.getTime())) return `${from} – ${to}`;

  const part = (date: Date, options: Intl.DateTimeFormatOptions) =>
    new Intl.DateTimeFormat("en-GB", { ...options, timeZone: "UTC" }).format(date);
  const full = { day: "numeric", month: "short", year: "numeric" } as const;

  if (start.getUTCFullYear() !== end.getUTCFullYear()) return `${part(start, full)} – ${part(end, full)}`;
  if (start.getUTCMonth() !== end.getUTCMonth()) return `${part(start, { day: "numeric", month: "short" })} – ${part(end, full)}`;
  return `${part(start, { day: "numeric" })} – ${part(end, full)}`;
}

/** The strip above the cards: which window is shown, and how many tickets it holds. */
function WindowStrip({ preset, from, to, totalTickets }: { preset: SummaryPreset; from: string; to: string; totalTickets: number }) {
  return (
    <div
      className="bg-card inline-flex flex-wrap items-center gap-x-3 gap-y-1 rounded-xl border px-4 py-2 text-sm"
      data-testid="summary-window"
    >
      <span className="text-muted-foreground text-xs font-semibold tracking-wider uppercase">{PRESET_LABELS[preset]}</span>
      <span className="text-foreground font-semibold">{formatWindow(from, to)}</span>
      <span className="text-muted-foreground">
        · <span className="text-foreground font-semibold tabular-nums">{totalTickets.toLocaleString()}</span> tickets raised
      </span>
    </div>
  );
}

function GlanceChip({ label, children, muted = false }: { label: string; children: ReactNode; muted?: boolean }) {
  return (
    <div
      className={`inline-flex items-center gap-2 rounded-md border px-3 py-1.5 text-sm ${
        muted ? "bg-muted/40 border-border" : "bg-success/5 border-success/20"
      }`}
    >
      <span className="text-muted-foreground text-[10px] font-semibold tracking-wider uppercase">{label}</span>
      <span className="text-foreground font-semibold">{children}</span>
    </div>
  );
}

/** Headline numbers for the window: total raised plus the top item of each breakdown. */
function AtAGlanceCard({ data }: { data: SummaryData }) {
  const top = (counts: SummaryCount[]): SummaryCount | undefined => counts[0];
  const topDriver = top(data.drivers);
  const topCategory = top(data.categories);
  const topFeature = top(data.features);
  const topTeam = top(data.teams);
  const driverShare = topDriver ? sharePercent(topDriver.count, sumCounts(data.drivers)) : null;
  const lastUpdated = data.summary.generatedAt;

  return (
    <div className="bg-card rounded-xl border" data-testid="summary-at-a-glance">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b px-6 py-4">
        <h2 className="text-foreground text-base font-semibold">At a glance</h2>
        {lastUpdated && (
          <p className="text-muted-foreground text-sm">
            Last updated <span className="text-foreground font-semibold">{formatGeneratedAt(lastUpdated)}</span>
          </p>
        )}
      </div>
      <div className="border-b px-6 py-4">
        <SummarySectionBody section={data.summary} />
      </div>
      <div className="flex flex-wrap gap-2 px-6 py-4">
        <GlanceChip label="Raised">{data.totalTickets.toLocaleString()} tickets</GlanceChip>
        {topDriver && (
          <GlanceChip label="Top driver">
            {topDriver.label} · {topDriver.count.toLocaleString()}
            {driverShare !== null && ` (${driverShare}%)`}
          </GlanceChip>
        )}
        {topCategory && (
          <GlanceChip label="Top subject">
            {topCategory.label} · {topCategory.count.toLocaleString()}
          </GlanceChip>
        )}
        {topFeature && (
          <GlanceChip label="Top feature">
            {topFeature.label} · {topFeature.count.toLocaleString()}
          </GlanceChip>
        )}
        {topTeam && (
          <GlanceChip label="Top tenant">
            {topTeam.label} · {topTeam.count.toLocaleString()}
          </GlanceChip>
        )}
        {data.unclassifiedTickets > 0 && (
          <GlanceChip label="Awaiting classification" muted>
            {data.unclassifiedTickets.toLocaleString()}
          </GlanceChip>
        )}
      </div>
    </div>
  );
}

function CountWithShare({ count, share }: { count: number; share: number }) {
  return (
    <span className="flex shrink-0 items-baseline gap-1.5">
      <span className="text-foreground font-mono text-base font-semibold tabular-nums">{count.toLocaleString()}</span>
      <span className="text-muted-foreground w-8 text-right font-mono text-xs tabular-nums">{share}%</span>
    </span>
  );
}

function formatTicketTimestamp(timestamp: string): string {
  const parsed = new Date(timestamp);
  if (isNaN(parsed.getTime())) return timestamp;
  return new Intl.DateTimeFormat("en-GB", {
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
    timeZone: "UTC",
  }).format(parsed);
}

/** Tracks which row labels are expanded within one card; everything starts collapsed. */
function useExpandedRows() {
  const [expanded, setExpanded] = useState<Set<string>>(() => new Set());
  const toggle = (label: string) =>
    setExpanded((previous) => {
      const next = new Set(previous);
      if (next.has(label)) next.delete(label);
      else next.add(label);
      return next;
    });
  return { isExpanded: (label: string) => expanded.has(label), toggle };
}

/** The expanded body of a breakdown row: its newest tickets. */
function RecentTicketsPanel({
  id,
  tickets,
  onOpenTicket,
}: {
  id: string;
  tickets: SummaryTicket[];
  onOpenTicket: (ticketId: string) => void;
}) {
  return (
    <div id={id} className="animate-in fade-in slide-in-from-top-1 mt-3 duration-[400ms]">
      <p className="text-muted-foreground mb-2 text-xs">Up to 5 most recent tickets</p>
      {tickets.length === 0 ? (
        <p className="text-muted-foreground text-sm">No tickets to show</p>
      ) : (
        <div className="space-y-1.5">
          {tickets.map((ticket) => (
            <RecentTicketRow key={ticket.ticketId} ticket={ticket} onOpen={onOpenTicket} />
          ))}
        </div>
      )}
    </div>
  );
}

function RecentTicketRow({ ticket, onOpen }: { ticket: SummaryTicket; onOpen: (ticketId: string) => void }) {
  return (
    <button
      type="button"
      aria-label={`View ticket ${ticket.ticketId}`}
      onClick={() => onOpen(ticket.ticketId)}
      className="bg-muted/40 hover:bg-muted flex w-full cursor-pointer items-center justify-between gap-3 rounded-md border px-3 py-2 text-left transition-colors"
    >
      <p className="text-foreground min-w-0 flex-1 text-sm">{ticket.text || `Ticket ${ticket.ticketId}`}</p>
      <span className="text-muted-foreground shrink-0 text-xs whitespace-nowrap">{formatTicketTimestamp(ticket.timestamp)}</span>
    </button>
  );
}

/**
 * A ranked list with a bar per row, each row showing its count and share of the breakdown. Rows
 * expand to their newest tickets.
 */
function BreakdownCard({
  title,
  counts,
  accent,
  onOpenTicket,
}: {
  title: string;
  counts: SummaryCount[];
  accent: Accent;
  onOpenTicket: (ticketId: string) => void;
}) {
  const colors = ACCENTS[accent];
  const total = sumCounts(counts);
  const max = counts.reduce((highest, count) => Math.max(highest, count.count), 0) || 1;
  const { isExpanded, toggle } = useExpandedRows();
  const idPrefix = title.toLowerCase().replace(/[^a-z0-9]+/g, "-");

  return (
    <div className="bg-card rounded-xl border p-6">
      <h2 className="text-foreground mb-4 text-base font-semibold">{title}</h2>
      {counts.length === 0 ? (
        <p className="text-muted-foreground p-16 text-center text-sm">No data for this period</p>
      ) : (
        <div className="space-y-1">
          {counts.map((count, index) => {
            const expanded = isExpanded(count.label);
            const contentId = `${idPrefix}-${index}-recent`;
            return (
              <div key={count.label}>
                <button
                  type="button"
                  onClick={() => toggle(count.label)}
                  aria-expanded={expanded}
                  aria-controls={contentId}
                  className="hover:bg-muted/40 -mx-2 flex w-[calc(100%+1rem)] cursor-pointer items-center gap-3 rounded-md px-2 py-2 text-left transition-colors"
                >
                  <div
                    className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-md ${colors.badge} font-mono text-xs font-semibold tabular-nums`}
                  >
                    {index + 1}
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center justify-between gap-3">
                      <h3 className="text-foreground truncate text-sm font-medium">{count.label}</h3>
                      <CountWithShare count={count.count} share={sharePercent(count.count, total)} />
                    </div>
                    {count.topProduct && <p className="text-muted-foreground mt-0.5 truncate text-xs">Top product: {count.topProduct}</p>}
                    <div className={`mt-2 h-1.5 rounded-full ${colors.track} overflow-hidden`}>
                      <div
                        className={`h-full rounded-full ${colors.bar} transition-all duration-500 ease-out`}
                        style={{ width: `${Math.round((count.count / max) * 100)}%` }}
                      />
                    </div>
                  </div>
                  <ChevronDown
                    className={`text-muted-foreground h-4 w-4 shrink-0 transition-transform duration-[400ms] ${expanded ? "" : "-rotate-90"}`}
                  />
                </button>
                {expanded && <RecentTicketsPanel id={contentId} tickets={count.recent} onOpenTicket={onOpenTicket} />}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

/**
 * The drivers breakdown: a stacked bar showing how the window splits across drivers, then one
 * colour-matched row per driver. Each row expands to its newest tickets.
 */
function DriverBreakdownCard({
  title,
  counts,
  onOpenTicket,
}: {
  title: string;
  counts: SummaryCount[];
  onOpenTicket: (ticketId: string) => void;
}) {
  const total = sumCounts(counts);
  const max = counts.reduce((highest, count) => Math.max(highest, count.count), 0) || 1;
  const colorFor = (index: number) => DRIVER_COLORS[index % DRIVER_COLORS.length];
  const { isExpanded, toggle } = useExpandedRows();

  return (
    <div className="bg-card rounded-xl border p-6" data-testid="summary-drivers">
      <h2 className="text-foreground mb-4 text-base font-semibold">{title}</h2>
      {counts.length === 0 ? (
        <p className="text-muted-foreground p-16 text-center text-sm">No data for this period</p>
      ) : (
        <>
          <div className="mb-6 flex h-8 w-full overflow-hidden rounded-md" role="img" aria-label="Share of tickets by driver">
            {counts.map((count, index) => {
              const share = sharePercent(count.count, total);
              return (
                <div
                  key={count.label}
                  className={`flex items-center justify-center ${colorFor(index)} font-mono text-xs font-semibold text-white tabular-nums transition-all duration-500 ease-out`}
                  style={{ width: `${total > 0 ? (count.count / total) * 100 : 0}%` }}
                  title={`${count.label}: ${count.count.toLocaleString()} (${share}%)`}
                >
                  {share >= 8 && `${share}%`}
                </div>
              );
            })}
          </div>
          <div className="divide-y">
            {counts.map((count, index) => {
              const expanded = isExpanded(count.label);
              const contentId = `driver-${index}-recent`;
              return (
                <div key={count.label} className="py-3 first:pt-0 last:pb-0">
                  <button
                    type="button"
                    onClick={() => toggle(count.label)}
                    aria-expanded={expanded}
                    aria-controls={contentId}
                    className="hover:bg-muted/40 -mx-2 flex w-[calc(100%+1rem)] cursor-pointer items-center gap-4 rounded-md px-2 py-1 text-left transition-colors"
                  >
                    <div className="flex w-56 shrink-0 items-center gap-2.5">
                      <span className={`h-3 w-3 shrink-0 rounded-sm ${colorFor(index)}`} />
                      <h3 className="text-foreground truncate text-sm font-semibold">{count.label}</h3>
                    </div>
                    <div className="bg-muted h-2 flex-1 overflow-hidden rounded-full">
                      <div
                        className={`h-full rounded-full ${colorFor(index)} transition-all duration-500 ease-out`}
                        style={{ width: `${Math.round((count.count / max) * 100)}%` }}
                      />
                    </div>
                    <CountWithShare count={count.count} share={sharePercent(count.count, total)} />
                    <ChevronDown
                      className={`text-muted-foreground h-4 w-4 shrink-0 transition-transform duration-[400ms] ${expanded ? "" : "-rotate-90"}`}
                    />
                  </button>
                  {expanded && <RecentTicketsPanel id={contentId} tickets={count.recent} onOpenTicket={onOpenTicket} />}
                </div>
              );
            })}
          </div>
        </>
      )}
    </div>
  );
}

/**
 * The prose section, rendered inside the "At a glance" card. It carries its own state, so a
 * failure here degrades to an inline message and never hides the headline chips or breakdowns.
 */
function SummarySectionBody({ section }: { section: SummarySection }) {
  if (section.state === "generating") {
    const analysed = section.progress?.analysedThreads ?? null;
    const total = section.progress?.totalThreads ?? null;
    const percent = total && total > 0 && analysed !== null ? Math.round((analysed / total) * 100) : null;
    const message =
      section.progress?.phase === "summarising"
        ? "Writing the summary..."
        : analysed !== null && total !== null && total > 0
          ? `Analysing threads... ${analysed} of ${total} complete`
          : "Checking for threads to analyse...";

    return (
      <div>
        <div className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className="border-secondary inline-block h-5 w-5 shrink-0 animate-spin rounded-full border-b-2"></div>
            <span className="text-foreground text-sm font-medium">{message}</span>
          </div>
          {percent !== null && <span className="text-foreground font-mono text-sm font-medium tabular-nums">{percent}%</span>}
        </div>
        {percent !== null && (
          <div className="bg-muted mt-3 h-2.5 overflow-hidden rounded-full">
            <div className="bg-secondary h-full rounded-full transition-all duration-500 ease-out" style={{ width: `${percent}%` }} />
          </div>
        )}
      </div>
    );
  }

  if (section.state === "unavailable") {
    return (
      <div>
        <div className="text-destructive flex items-center gap-2 text-sm">
          <AlertCircle className="h-4 w-4 shrink-0" />
          <span>{section.error ?? "The summary could not be generated."}</span>
        </div>
        <p className="text-muted-foreground mt-2 text-sm">The breakdowns below are unaffected.</p>
      </div>
    );
  }

  return (
    <div>
      <p className="text-foreground text-sm whitespace-pre-wrap">{section.content}</p>
      {(section.model || section.generatedAt) && (
        <p className="text-muted-foreground mt-4 text-xs">
          {section.model && <>Generated by {section.model}</>}
          {section.model && section.generatedAt && " · "}
          {section.generatedAt && formatGeneratedAt(section.generatedAt)}
        </p>
      )}
    </div>
  );
}

export default function SupportSummaryPage() {
  const [params, setParams] = useUrlParams(
    // Widened to string so `setParams` accepts any preset, not just the default's literal type.
    { dateFilter: DEFAULT_PRESET as string, dateFrom: "", dateTo: "" },
    {
      dateFilter: enumValidator(SUMMARY_PRESETS, DEFAULT_PRESET),
      dateFrom: isoDateValidator,
      dateTo: isoDateValidator,
    }
  );
  // Safe to cast: the validator guarantees a valid preset.
  const dateFilter = params.dateFilter as SummaryPreset;
  const isDateRangeValid = !params.dateFrom || !params.dateTo || params.dateFrom <= params.dateTo;

  // Falls back to the default preset while a custom range is incomplete or inverted, so the
  // page always has a window to load rather than sitting empty.
  const summaryWindow = useMemo(() => {
    if (dateFilter === "custom" && params.dateFrom && params.dateTo && params.dateFrom <= params.dateTo) {
      return { from: params.dateFrom, to: params.dateTo };
    }
    const preset = dateFilter === "custom" ? DEFAULT_PRESET : dateFilter;
    return windowEndingYesterday(PRESET_DAYS[preset]);
  }, [dateFilter, params.dateFrom, params.dateTo]);

  const { data, isLoading, error } = useSummary(summaryWindow.from, summaryWindow.to);
  // Same rule as the Products View tab: only shown once the registry confirms product tags exist,
  // so it never flashes in and out while the registry loads.
  const { data: registryData } = useRegistry();
  const productTagsConfigured = hasActiveProductTags(registryData);

  const queryClient = useQueryClient();
  const [selectedTicketId, setSelectedTicketId] = useState<string | null>(null);
  const [isTicketModalOpen, setIsTicketModalOpen] = useState(false);

  const openTicket = (ticketId: string) => {
    setSelectedTicketId(ticketId);
    setIsTicketModalOpen(true);
  };

  const handleTicketSaved = () => {
    if (selectedTicketId) {
      queryClient.invalidateQueries({ queryKey: ["ticket", selectedTicketId] });
    }
    queryClient.invalidateQueries({ queryKey: ["tickets"] });
    // A team or status change moves the ticket between breakdown buckets.
    queryClient.invalidateQueries({ queryKey: ["summary"] });
  };

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-foreground text-2xl font-bold">Support Summary</h1>
          <p className="text-muted-foreground text-sm">What tenants raised in the selected period, and why</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Select
            value={dateFilter}
            onValueChange={(value) =>
              setParams(value !== "custom" ? { dateFilter: value, dateFrom: "", dateTo: "" } : { dateFilter: value })
            }
          >
            <SelectTrigger className="w-[160px]" data-testid="summary-date-filter">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="lastWeek">Last Week</SelectItem>
              <SelectItem value="last2Weeks">Last 2 Weeks</SelectItem>
              <SelectItem value="lastMonth">Last Month</SelectItem>
              <SelectItem value="custom">Custom</SelectItem>
            </SelectContent>
          </Select>
          {dateFilter === "custom" && (
            <>
              <Input
                type="date"
                aria-label="From date"
                value={params.dateFrom}
                onChange={(event) => setParams({ dateFrom: event.target.value })}
                className="w-[150px]"
              />
              <Input
                type="date"
                aria-label="To date"
                value={params.dateTo}
                onChange={(event) => setParams({ dateTo: event.target.value })}
                className="w-[150px]"
              />
            </>
          )}
          {dateFilter === "custom" && !isDateRangeValid && <span className="text-destructive text-xs font-medium">Invalid range</span>}
        </div>
      </div>

      {isLoading && !data && (
        <div className="flex h-full items-center justify-center">
          <div className="text-center">
            <div className="border-primary mb-4 inline-block h-12 w-12 animate-spin rounded-full border-b-2"></div>
            <p className="text-muted-foreground">Loading support summary...</p>
          </div>
        </div>
      )}

      {error && !data && (
        <div className="flex h-full items-center justify-center">
          <div className="text-center">
            <p className="text-destructive">Error loading support summary</p>
            <p className="text-muted-foreground mt-2 text-sm">Please try again later</p>
          </div>
        </div>
      )}

      {data && (
        <>
          <WindowStrip preset={dateFilter} from={data.from} to={data.to} totalTickets={data.totalTickets} />

          <AtAGlanceCard data={data} />

          <DriverBreakdownCard title="Top Support Areas" counts={data.drivers} onOpenTicket={openTicket} />

          <BreakdownCard title="Top categories" counts={data.categories} accent="info" onOpenTicket={openTicket} />

          {/* Products sit beside knowledge gaps when configured; otherwise knowledge gaps take the row. */}
          <div className={`grid grid-cols-1 gap-6 ${productTagsConfigured ? "lg:grid-cols-2" : ""}`}>
            <BreakdownCard title="Top knowledge gaps" counts={data.knowledgeGaps} accent="warning" onOpenTicket={openTicket} />
            {productTagsConfigured && (
              <BreakdownCard title="Top products" counts={data.products} accent="primary" onOpenTicket={openTicket} />
            )}
          </div>

          <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
            <BreakdownCard title="Top Platform Features" counts={data.features} accent="success" onOpenTicket={openTicket} />
            <BreakdownCard title="Top Teams" counts={data.teams} accent="purple" onOpenTicket={openTicket} />
          </div>
        </>
      )}

      <EditTicketModal
        ticketId={selectedTicketId}
        open={isTicketModalOpen}
        onOpenChange={(open) => {
          setIsTicketModalOpen(open);
          if (!open) setSelectedTicketId(null);
        }}
        onSuccess={handleTicketSaved}
      />
    </div>
  );
}
