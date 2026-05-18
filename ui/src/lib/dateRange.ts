export type CustomDateRange = { start?: string; end?: string };

export type TimeBucketResolution = "hour" | "day" | "week" | "month";

/** All canonical date-filter presets shared across the application. */
export type DateFilter = "lastWeek" | "last2Weeks" | "lastMonth" | "lastYear" | "custom" | "all";

/**
 * Standard day offsets for every named preset.
 * Pages that expose only a subset can pick the keys they need.
 */
export const PRESET_DAYS: Record<Exclude<DateFilter, "custom" | "all">, number> = {
  lastWeek: 7,
  last2Weeks: 14,
  lastMonth: 30,
  lastYear: 365,
};

type DateRangeOptions<T extends string> = {
  dateFilter: T;
  customDateRange: CustomDateRange;
  customValue: T;
  fallbackValue: Exclude<T, "custom" | "all">;
  allValue?: T;
  presetDays: Partial<Record<Exclude<T, "custom" | "all">, number>>;
};

const toDateString = (date: Date): string => date.toISOString().split("T")[0];

function toValidDate(value?: string | Date): Date | undefined {
  if (!value) {
    return undefined;
  }

  const date = value instanceof Date ? value : new Date(value);
  return Number.isNaN(date.getTime()) ? undefined : date;
}

export function formatTimeBucketLabel(value: string | Date, resolution: TimeBucketResolution): string {
  const date = toValidDate(value);
  if (!date) {
    console.warn(`formatTimeBucketLabel: unable to parse date value: ${String(value)}`);
    return String(value);
  }

  const timeZone = "UTC";
  switch (resolution) {
    case "hour":
      return date.toLocaleString("en-US", {
        month: "short",
        day: "numeric",
        year: "numeric",
        hour: "numeric",
        timeZone,
      });
    case "day":
    case "week":
      return date.toLocaleDateString("en-US", { month: "short", day: "numeric", timeZone });
    case "month":
      return date.toLocaleDateString("en-US", { month: "short", year: "numeric", timeZone });
  }
}

export function getDateRangeFromFilter<T extends string>({
  dateFilter,
  customDateRange,
  customValue,
  fallbackValue,
  allValue,
  presetDays,
}: DateRangeOptions<T>): { from?: string; to?: string } {
  if (allValue && dateFilter === allValue) {
    return { from: undefined, to: undefined };
  }

  if (dateFilter === customValue) {
    if (customDateRange.start && customDateRange.end) {
      return { from: customDateRange.start, to: customDateRange.end };
    }
    return getDateRangeFromFilter({
      dateFilter: fallbackValue as T,
      customDateRange,
      customValue,
      fallbackValue,
      allValue,
      presetDays,
    });
  }

  const days = presetDays[dateFilter as Exclude<T, "custom" | "all">];
  if (days == null) {
    return { from: undefined, to: undefined };
  }

  const now = new Date();
  const to = toDateString(now);
  const fromDate = new Date(now);
  fromDate.setDate(now.getDate() - days);
  return { from: toDateString(fromDate), to };
}
