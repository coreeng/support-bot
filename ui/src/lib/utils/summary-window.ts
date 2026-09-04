// src/lib/utils/summary-window.ts

/** Formats a date as its UTC calendar day, `YYYY-MM-DD`. */
export const toUtcDateString = (date: Date): string => date.toISOString().split("T")[0];

/**
 * A window of `days` inclusive UTC days ending yesterday (UTC) — today is unfinished, and
 * excluding it keeps the window (and so the cached summary) stable for the whole day.
 *
 * Everything is computed in UTC so the result matches the server default
 * (`LocalDate.now(UTC).minusDays(1)`) in every zone. Mixing local-calendar arithmetic with
 * `toISOString()` would shift the window by a day across a local DST change.
 */
export function windowEndingYesterday(days: number, now: Date = new Date()): { from: string; to: string } {
  const end = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() - 1));
  const start = new Date(end);
  start.setUTCDate(end.getUTCDate() - (days - 1));
  return { from: toUtcDateString(start), to: toUtcDateString(end) };
}

/** The longest window the backend accepts, both ends included (`SummaryController.MAX_WINDOW_DAYS`). */
export const MAX_SUMMARY_WINDOW_DAYS = 366;

export type SummaryWindowProblem = "inverted" | "tooLong";

/** Number of inclusive calendar days from `from` to `to` (both `YYYY-MM-DD`), e.g. one day when equal. */
const inclusiveDays = (from: string, to: string): number =>
  Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86_400_000) + 1;

/**
 * Why the backend would reject a `from`..`to` window with `SUMMARY_WINDOW_INVALID`, or `null`
 * when it would accept it. Mirrors the server rules so the page can refuse the range up front.
 */
export function summaryWindowProblem(from: string, to: string): SummaryWindowProblem | null {
  if (to < from) return "inverted";
  if (inclusiveDays(from, to) > MAX_SUMMARY_WINDOW_DAYS) return "tooLong";
  return null;
}
