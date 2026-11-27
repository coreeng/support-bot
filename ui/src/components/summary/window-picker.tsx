"use client";

import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { enumValidator, isoDateValidator } from "@/lib/hooks/useUrlParams";
import { MAX_SUMMARY_WINDOW_DAYS, presetWindow, summaryWindowProblem, type SummaryWindowPreset } from "@/lib/utils/summary-window";

/**
 * The presets the window picker offers. Each named one is a whole business period before the current
 * one (see `presetWindow`); the default is the two weeks before this one.
 */
const SUMMARY_PRESETS = ["lastWeek", "last2Weeks", "lastMonth", "custom"] as const satisfies readonly SummaryPreset[];
export type SummaryPreset = SummaryWindowPreset | "custom";

const DEFAULT_PRESET: SummaryWindowPreset = "last2Weeks";

export const PRESET_LABELS: Record<SummaryPreset, string> = {
  lastWeek: "Last week",
  last2Weeks: "Last 2 weeks",
  lastMonth: "Last month",
  custom: "Custom range",
};

export type WindowParams = {
  dateFilter: string;
  dateFrom: string;
  dateTo: string;
};

/** The URL parameters the window picker reads and writes, for `useUrlParams`. */
export const WINDOW_PARAM_DEFAULTS: WindowParams = { dateFilter: DEFAULT_PRESET, dateFrom: "", dateTo: "" };

export const WINDOW_PARAM_VALIDATORS = {
  dateFilter: enumValidator(SUMMARY_PRESETS, DEFAULT_PRESET),
  dateFrom: isoDateValidator,
  dateTo: isoDateValidator,
};

/**
 * The window to load. Falls back to the default preset while a custom range is incomplete or
 * invalid, so the page always has a window to load rather than sitting empty.
 */
export function chosenWindow({ dateFilter, dateFrom, dateTo }: WindowParams): { from: string; to: string } {
  if (dateFilter === "custom" && dateFrom && dateTo && summaryWindowProblem(dateFrom, dateTo) === null) {
    return { from: dateFrom, to: dateTo };
  }
  const preset = dateFilter === "custom" ? DEFAULT_PRESET : (dateFilter as SummaryWindowPreset);
  return presetWindow(preset);
}

/**
 * Formats an inclusive date window compactly, sharing whatever the two ends have in common:
 * "12 – 25 Aug 2026", "28 Jul – 25 Aug 2026", "28 Dec 2025 – 10 Jan 2026".
 */
export function formatWindow(from: string, to: string): string {
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

/**
 * The preset whose business period is exactly this window, or `custom` when none is.
 * Deriving the label from the window on screen keeps it honest while a newly chosen preset is
 * still loading and the previous window's figures are shown in its place.
 */
export function presetForWindow(from: string, to: string): SummaryPreset {
  for (const preset of SUMMARY_PRESETS) {
    if (preset === "custom") continue;
    const window = presetWindow(preset);
    if (window.from === from && window.to === to) return preset;
  }
  return "custom";
}

/** Inline guidance next to the custom range inputs; nothing while the range is valid or incomplete. */
const RANGE_PROBLEM_LABELS = {
  inverted: "Invalid range: end date is before start date",
  tooLong: `Invalid range: ${MAX_SUMMARY_WINDOW_DAYS} days at most`,
} as const;

/** The preset dropdown, with from/to inputs for a custom range. */
export function WindowPicker({
  params,
  onChange,
  testId,
}: {
  params: WindowParams;
  onChange: (updates: Partial<WindowParams>) => void;
  testId: string;
}) {
  // Mirrors the backend's window rules, so a range it would reject with 400 is never requested.
  const rangeProblem = params.dateFrom && params.dateTo ? summaryWindowProblem(params.dateFrom, params.dateTo) : null;
  const custom = params.dateFilter === "custom";

  return (
    <>
      <Select
        value={params.dateFilter}
        onValueChange={(value) => onChange(value !== "custom" ? { dateFilter: value, dateFrom: "", dateTo: "" } : { dateFilter: value })}
      >
        <SelectTrigger className="w-[160px]" data-testid={testId}>
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="lastWeek">Last Week</SelectItem>
          <SelectItem value="last2Weeks">Last 2 Weeks</SelectItem>
          <SelectItem value="lastMonth">Last Month</SelectItem>
          <SelectItem value="custom">Custom</SelectItem>
        </SelectContent>
      </Select>
      {custom && (
        <>
          <Input
            type="date"
            aria-label="From date"
            value={params.dateFrom}
            onChange={(event) => onChange({ dateFrom: event.target.value })}
            className="w-[150px]"
          />
          <Input
            type="date"
            aria-label="To date"
            value={params.dateTo}
            onChange={(event) => onChange({ dateTo: event.target.value })}
            className="w-[150px]"
          />
        </>
      )}
      {custom && rangeProblem !== null && (
        <span className="text-destructive text-xs font-medium" role="alert">
          {RANGE_PROBLEM_LABELS[rangeProblem]}
        </span>
      )}
    </>
  );
}
