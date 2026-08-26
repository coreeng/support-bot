"use client";

import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { PRESET_DAYS } from "@/lib/dateRange";
import { useSummary } from "@/lib/hooks";
import { enumValidator, isoDateValidator, useUrlParams } from "@/lib/hooks/useUrlParams";
import type { SummaryCount, SummarySection } from "@/lib/types/summary";
import { AlertCircle } from "lucide-react";
import { useMemo } from "react";

/** The presets this page offers; the default window is the last 2 weeks ending yesterday. */
const SUMMARY_PRESETS = ["lastWeek", "last2Weeks", "lastMonth", "custom"] as const;
type SummaryPreset = (typeof SUMMARY_PRESETS)[number];

const DEFAULT_PRESET: Exclude<SummaryPreset, "custom"> = "last2Weeks";

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
} as const;

type Accent = keyof typeof ACCENTS;

function formatGeneratedAt(timestamp: string): string {
  const parsed = new Date(timestamp);
  if (isNaN(parsed.getTime())) return timestamp;
  return new Intl.DateTimeFormat("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
    hour12: true,
    timeZone: "UTC",
  }).format(parsed);
}

function formatWindow(from: string, to: string): string {
  const format = (value: string) => {
    const parsed = new Date(`${value}T12:00:00Z`);
    if (isNaN(parsed.getTime())) return value;
    return new Intl.DateTimeFormat("en-US", { month: "short", day: "numeric", year: "numeric", timeZone: "UTC" }).format(parsed);
  };
  return `${format(from)} – ${format(to)}`;
}

function StatCard({
  label,
  value,
  accent,
  valueClass = "text-foreground",
}: {
  label: string;
  value: number;
  accent: string;
  valueClass?: string;
}) {
  return (
    <div className="bg-card relative overflow-hidden rounded-xl border p-6">
      <div className={`${accent} absolute -top-4 -right-4 h-24 w-24 rounded-full`} />
      <div className={`${accent} absolute -right-6 -bottom-6 h-20 w-20 rounded-full`} />
      <div className="relative">
        <p className="text-muted-foreground mb-2 text-sm font-medium">{label}</p>
        <p className={`${valueClass} font-mono text-3xl font-semibold tracking-tight tabular-nums`}>{value.toLocaleString()}</p>
      </div>
    </div>
  );
}

function BreakdownCard({ title, counts, accent }: { title: string; counts: SummaryCount[]; accent: Accent }) {
  const colors = ACCENTS[accent];
  const max = counts.reduce((highest, count) => Math.max(highest, count.count), 0) || 1;

  return (
    <div className="bg-card rounded-xl border p-6">
      <h2 className="text-foreground mb-4 text-base font-semibold">{title}</h2>
      {counts.length === 0 ? (
        <p className="text-muted-foreground p-16 text-center text-sm">No data for this period</p>
      ) : (
        <div className="space-y-3">
          {counts.map((count, index) => (
            <div key={count.label} className="flex items-center gap-3">
              <div
                className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-md ${colors.badge} font-mono text-xs font-semibold tabular-nums`}
              >
                {index + 1}
              </div>
              <div className="min-w-0 flex-1">
                <div className="flex items-center justify-between gap-3">
                  <h3 className="text-foreground truncate text-sm font-medium">{count.label}</h3>
                  <span className="text-muted-foreground shrink-0 font-mono text-xs tabular-nums">{count.count.toLocaleString()}</span>
                </div>
                <div className={`mt-2 h-1.5 rounded-full ${colors.track} overflow-hidden`}>
                  <div
                    className={`h-full rounded-full ${colors.bar} transition-all duration-500 ease-out`}
                    style={{ width: `${Math.round((count.count / max) * 100)}%` }}
                  />
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

/**
 * The prose section. It carries its own state, so a failure here degrades to an inline
 * message and never hides the breakdowns.
 */
function SummarySectionCard({ section }: { section: SummarySection }) {
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
      <div className="bg-card rounded-xl border p-6">
        <h2 className="text-foreground mb-4 text-base font-semibold">Summary</h2>
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
      <div className="bg-card rounded-xl border p-6">
        <h2 className="text-foreground mb-4 text-base font-semibold">Summary</h2>
        <div className="text-destructive flex items-center gap-2 text-sm">
          <AlertCircle className="h-4 w-4 shrink-0" />
          <span>{section.error ?? "The summary could not be generated."}</span>
        </div>
        <p className="text-muted-foreground mt-2 text-sm">The breakdowns below are unaffected.</p>
      </div>
    );
  }

  return (
    <div className="bg-card rounded-xl border p-6">
      <h2 className="text-foreground mb-4 text-base font-semibold">Summary</h2>
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

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-foreground text-2xl font-bold">Support Summary</h1>
          <p className="text-muted-foreground text-sm">
            What tenants raised between {formatWindow(summaryWindow.from, summaryWindow.to)}, and why
          </p>
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
          <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
            <StatCard label="Tickets raised" value={data.totalTickets} accent="bg-primary/15" />
            <StatCard label="Classified" value={data.classifiedTickets} accent="bg-info/15" />
            <StatCard
              label="Awaiting classification"
              value={data.unclassifiedTickets}
              accent="bg-warning/15"
              valueClass={data.unclassifiedTickets > 0 ? "text-warning" : "text-foreground"}
            />
          </div>

          <SummarySectionCard section={data.summary} />

          <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
            <BreakdownCard title="Why tenants got in touch" counts={data.drivers} accent="primary" />
            <BreakdownCard title="Top categories" counts={data.categories} accent="info" />
            <BreakdownCard title="Platform features asked about" counts={data.features} accent="success" />
            <BreakdownCard title="Teams raising the most" counts={data.teams} accent="purple" />
          </div>
        </>
      )}
    </div>
  );
}
