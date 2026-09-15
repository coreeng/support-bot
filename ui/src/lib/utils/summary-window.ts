// src/lib/utils/summary-window.ts

/** Formats a date as its UTC calendar day, `YYYY-MM-DD`. */
export const toUtcDateString = (date: Date): string => date.toISOString().split("T")[0];

/** The presets the Support Summary offers, each a whole business period before the current one. */
export type SummaryPreset = "lastWeek" | "last2Weeks" | "lastMonth";

/** Midnight UTC on the Monday of the ISO week containing `date`. */
function mondayOfWeek(date: Date): Date {
  // getUTCDay(): Sunday is 0, so it sits six days after its Monday rather than one before.
  const daysSinceMonday = (date.getUTCDay() + 6) % 7;
  return new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate() - daysSinceMonday));
}

/** `date` moved by `days` on the UTC calendar. */
const plusDays = (date: Date, days: number): Date => new Date(date.getTime() + days * 86_400_000);

/**
 * The window a preset stands for, as inclusive UTC calendar days: the business period before the
 * one containing `now`, never the current one.
 *
 * - `lastWeek`: Monday to Friday of the previous week.
 * - `last2Weeks`: Monday two weeks back to Friday of the previous week.
 * - `lastMonth`: the first to the last day of the previous calendar month.
 *
 * Whole past periods keep the window, and so the cached summary, stable all week or month, and
 * make "last week" mean the same thing whichever day it is asked on. Everything is computed in
 * UTC so the result matches the server in every zone; mixing local-calendar arithmetic with
 * `toISOString()` would shift the window by a day across a local DST change.
 */
export function presetWindow(preset: SummaryPreset, now: Date = new Date()): { from: string; to: string } {
  const thisMonday = mondayOfWeek(now);
  switch (preset) {
    case "lastWeek":
      return { from: toUtcDateString(plusDays(thisMonday, -7)), to: toUtcDateString(plusDays(thisMonday, -3)) };
    case "last2Weeks":
      return { from: toUtcDateString(plusDays(thisMonday, -14)), to: toUtcDateString(plusDays(thisMonday, -3)) };
    case "lastMonth": {
      const firstOfThisMonth = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), 1));
      const firstOfLastMonth = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() - 1, 1));
      return { from: toUtcDateString(firstOfLastMonth), to: toUtcDateString(plusDays(firstOfThisMonth, -1)) };
    }
  }
}

/**
 * The longest window the backend accepts, both ends included: one quarter
 * (`SummaryController.MAX_WINDOW_DAYS`).
 */
export const MAX_SUMMARY_WINDOW_DAYS = 92;

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
