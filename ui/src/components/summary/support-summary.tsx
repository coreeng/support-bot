"use client";

import { hasActiveProductTags } from "@/components/products/products";
import BreakdownCard, { DISTINCT_ROW_COLORS, sharePercent, sumCounts } from "@/components/summary/breakdown-card";
import PromptDialog from "@/components/summary/prompt-dialog";
import EditTicketModal from "@/components/tickets/EditTicketModal";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { PRESET_DAYS } from "@/lib/dateRange";
import { isApiError, useRegistry, useSummary } from "@/lib/hooks";
import { enumValidator, isoDateValidator, useUrlParams } from "@/lib/hooks/useUrlParams";
import type { SummaryCount, SummaryData, SummarySection } from "@/lib/types/summary";
import { MAX_SUMMARY_WINDOW_DAYS, summaryWindowProblem, windowEndingYesterday } from "@/lib/utils/summary-window";
import { useQueryClient } from "@tanstack/react-query";
import { AlertCircle, Eye } from "lucide-react";
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

/** A count or percentage inline in prose; every metric is set in a monospaced, tabular face. */
function Metric({ children }: { children: ReactNode }) {
  return <span className="font-mono tabular-nums">{children}</span>;
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
        · <span className="text-foreground font-mono font-semibold tabular-nums">{totalTickets.toLocaleString()}</span> tickets raised
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
        <GlanceChip label="Raised">
          <Metric>{data.totalTickets.toLocaleString()}</Metric> tickets
        </GlanceChip>
        {topDriver && (
          <GlanceChip label="Top driver">
            {topDriver.label} · <Metric>{topDriver.count.toLocaleString()}</Metric>
            {driverShare !== null && (
              <>
                {" "}
                (<Metric>{driverShare}%</Metric>)
              </>
            )}
          </GlanceChip>
        )}
        {topCategory && (
          <GlanceChip label="Top subject">
            {topCategory.label} · <Metric>{topCategory.count.toLocaleString()}</Metric>
          </GlanceChip>
        )}
        {topFeature && (
          <GlanceChip label="Top feature">
            {topFeature.label} · <Metric>{topFeature.count.toLocaleString()}</Metric>
          </GlanceChip>
        )}
        {topTeam && (
          <GlanceChip label="Top tenant">
            {topTeam.label} · <Metric>{topTeam.count.toLocaleString()}</Metric>
          </GlanceChip>
        )}
        {data.unclassifiedTickets > 0 && (
          <GlanceChip label="Awaiting classification" muted>
            <Metric>{data.unclassifiedTickets.toLocaleString()}</Metric>
          </GlanceChip>
        )}
      </div>
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
    const message: ReactNode =
      section.progress?.phase === "summarising" ? (
        "Writing the summary..."
      ) : analysed !== null && total !== null && total > 0 ? (
        <>
          Analysing threads... <Metric>{analysed.toLocaleString()}</Metric> of <Metric>{total.toLocaleString()}</Metric> complete
        </>
      ) : (
        "Checking for threads to analyse..."
      );

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

/**
 * Explains a failed summary request. The backend's ProblemDetail `code` is forwarded by the API
 * route, so the page can tell a bad window or a missing prompt apart from an outage.
 */
export function summaryErrorMessage(error: unknown): { title: string; detail: string } {
  if (isApiError(error, 403)) {
    return { title: "You do not have permission to view the Support Summary", detail: "Ask an administrator for access." };
  }
  if (isApiError(error, 404)) {
    return { title: "Support Summary is not enabled", detail: "Enable the summary feature on the server to use this page." };
  }
  if (isApiError(error) && error.reason === "SUMMARY_WINDOW_INVALID") {
    return {
      title: "The selected range is invalid",
      detail: `The window must not exceed ${MAX_SUMMARY_WINDOW_DAYS} days, and the end date must not be before the start date.`,
    };
  }
  if (isApiError(error) && error.reason === "ANALYSIS_PROMPT_LOAD_FAILED") {
    return {
      title: "The classification prompt could not be loaded",
      detail: "The summary cannot be computed until the analysis prompt configuration on the server is fixed.",
    };
  }
  return { title: "Error loading support summary", detail: "Please try again later" };
}

/** Inline guidance next to the custom range inputs; nothing while the range is valid or incomplete. */
const RANGE_PROBLEM_LABELS = {
  inverted: "Invalid range: end date is before start date",
  tooLong: `Invalid range: ${MAX_SUMMARY_WINDOW_DAYS} days at most`,
} as const;

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
  // Mirrors the backend's window rules, so a range it would reject with 400 is never requested.
  const rangeProblem = params.dateFrom && params.dateTo ? summaryWindowProblem(params.dateFrom, params.dateTo) : null;

  // Falls back to the default preset while a custom range is incomplete or invalid, so the
  // page always has a window to load rather than sitting empty.
  const summaryWindow = useMemo(() => {
    if (dateFilter === "custom" && params.dateFrom && params.dateTo && summaryWindowProblem(params.dateFrom, params.dateTo) === null) {
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
  const [isPromptDialogOpen, setIsPromptDialogOpen] = useState(false);

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
          <Button type="button" variant="outline" size="default" onClick={() => setIsPromptDialogOpen(true)}>
            <Eye className="h-4 w-4" />
            View Prompts
          </Button>
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
          {dateFilter === "custom" && rangeProblem !== null && (
            <span className="text-destructive text-xs font-medium" role="alert">
              {RANGE_PROBLEM_LABELS[rangeProblem]}
            </span>
          )}
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
        <div className="flex h-full items-center justify-center" data-testid="summary-error">
          <div className="text-center">
            <p className="text-destructive">{summaryErrorMessage(error).title}</p>
            <p className="text-muted-foreground mt-2 text-sm">{summaryErrorMessage(error).detail}</p>
          </div>
        </div>
      )}

      {data && (
        <>
          <WindowStrip preset={dateFilter} from={data.from} to={data.to} totalTickets={data.totalTickets} />

          <AtAGlanceCard data={data} />

          <BreakdownCard
            title="Top Support Areas"
            counts={data.drivers}
            palette={DISTINCT_ROW_COLORS}
            stackedBar="Share of tickets by driver"
            onOpenTicket={openTicket}
            testId="summary-drivers"
          />

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
            <BreakdownCard
              title="Top Teams"
              counts={data.teams}
              accent="purple"
              subtitle={(team) => (team.topProduct ? `Top product: ${team.topProduct}` : undefined)}
              onOpenTicket={openTicket}
            />
          </div>
        </>
      )}

      <PromptDialog open={isPromptDialogOpen} onOpenChange={setIsPromptDialogOpen} />

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
