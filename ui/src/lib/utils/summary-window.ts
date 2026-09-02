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
