"use client";

import type { SummaryCount, SummaryTicket } from "@/lib/types/summary";
import { ChevronDown } from "lucide-react";
import { useState } from "react";

/**
 * The ranked-breakdown widget used for every card on the Support Summary page: a title, one row per
 * value with a bar, count and share, and rows that expand to their newest tickets.
 *
 * Options:
 * - `accent` — a single colour for every row, with a numbered badge in front of each (the default).
 * - `palette` — one colour per row, with a swatch in front of each; ranks beyond the palette wrap.
 * - `stackedBar` — a share bar above the rows split by row colour; meaningful with a `palette`.
 * - `subtitle` — a secondary line under a row's label, derived from the row (return nothing to omit).
 */
export const BREAKDOWN_ACCENTS = {
  primary: { bar: "bg-primary", track: "bg-primary/10", badge: "bg-primary/10 text-primary" },
  info: { bar: "bg-info", track: "bg-info/10", badge: "bg-info/10 text-info" },
  success: { bar: "bg-success", track: "bg-success/10", badge: "bg-success/10 text-success" },
  purple: { bar: "bg-chart-4", track: "bg-chart-4/10", badge: "bg-chart-4/10 text-chart-4" },
  warning: { bar: "bg-warning", track: "bg-warning/10", badge: "bg-warning/10 text-warning" },
} as const;

export type BreakdownAccent = keyof typeof BREAKDOWN_ACCENTS;

/**
 * Distinct row colours for a breakdown whose rows should be told apart, e.g. under a stacked bar.
 *
 * Chart tokens only, so they follow the theme. Ordered so neighbouring ranks differ in hue and the
 * top ranks — the segments wide enough to carry a label — take the tokens with the strongest
 * contrast against `text-primary-foreground`, which stays light in both themes.
 */
export const DISTINCT_ROW_COLORS = [
  "bg-chart-1",
  "bg-chart-7",
  "bg-chart-9",
  "bg-chart-2",
  "bg-chart-10",
  "bg-chart-6",
  "bg-chart-5",
] as const;

export const sumCounts = (counts: SummaryCount[]): number => counts.reduce((sum, count) => sum + count.count, 0);

export const sharePercent = (count: number, total: number): number => (total > 0 ? Math.round((count / total) * 100) : 0);

export interface BreakdownCardProps {
  title: string;
  counts: SummaryCount[];
  onOpenTicket: (ticketId: string) => void;
  /** Single colour for every row; ignored when `palette` is given. */
  accent?: BreakdownAccent;
  /** Per-row colours (Tailwind background classes), applied by rank. */
  palette?: readonly string[];
  /** Show a stacked share bar above the rows; the string is its accessible label. */
  stackedBar?: string;
  /** Secondary text under a row's label. */
  subtitle?: (count: SummaryCount) => string | undefined;
  emptyMessage?: string;
  testId?: string;
}

export default function BreakdownCard({
  title,
  counts,
  onOpenTicket,
  accent = "primary",
  palette,
  stackedBar,
  subtitle,
  emptyMessage = "No data for this period",
  testId,
}: BreakdownCardProps) {
  const accentColors = BREAKDOWN_ACCENTS[accent];
  const total = sumCounts(counts);
  const max = counts.reduce((highest, count) => Math.max(highest, count.count), 0) || 1;
  const { isExpanded, toggle } = useExpandedRows();
  const idPrefix = title.toLowerCase().replace(/[^a-z0-9]+/g, "-");

  const barColor = (index: number) => (palette ? palette[index % palette.length] : accentColors.bar);
  const trackColor = palette ? "bg-muted" : accentColors.track;

  return (
    <div className="bg-card rounded-xl border p-6" data-testid={testId}>
      <h2 className="text-foreground mb-4 text-base font-semibold">{title}</h2>
      {counts.length === 0 ? (
        <p className="text-muted-foreground p-16 text-center text-sm">{emptyMessage}</p>
      ) : (
        <>
          {stackedBar && <StackedShareBar counts={counts} total={total} colorFor={barColor} label={stackedBar} />}
          <div className="space-y-1">
            {counts.map((count, index) => {
              const expanded = isExpanded(count.label);
              const contentId = `${idPrefix}-${index}-recent`;
              const secondary = subtitle?.(count);
              return (
                <div key={count.label}>
                  <button
                    type="button"
                    onClick={() => toggle(count.label)}
                    aria-expanded={expanded}
                    aria-controls={contentId}
                    className="hover:bg-muted/40 -mx-2 flex w-[calc(100%+1rem)] cursor-pointer items-center gap-3 rounded-md px-2 py-2 text-left transition-colors"
                  >
                    {palette ? (
                      <span className={`h-3 w-3 shrink-0 rounded-sm ${barColor(index)}`} />
                    ) : (
                      <div
                        className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-md ${accentColors.badge} font-mono text-xs font-semibold tabular-nums`}
                      >
                        {index + 1}
                      </div>
                    )}
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center justify-between gap-3">
                        <h3 className="text-foreground truncate text-sm font-medium">{count.label}</h3>
                        <CountWithShare count={count.count} share={sharePercent(count.count, total)} />
                      </div>
                      {secondary && <p className="text-muted-foreground mt-0.5 truncate text-xs">{secondary}</p>}
                      <div className={`mt-2 h-1.5 rounded-full ${trackColor} overflow-hidden`}>
                        <div
                          className={`h-full rounded-full ${barColor(index)} transition-all duration-500 ease-out`}
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
        </>
      )}
    </div>
  );
}

/** One bar split by each row's share of the total; segments too thin for a label stay unlabelled. */
function StackedShareBar({
  counts,
  total,
  colorFor,
  label,
}: {
  counts: SummaryCount[];
  total: number;
  colorFor: (index: number) => string;
  label: string;
}) {
  return (
    <div className="mb-6 flex h-8 w-full overflow-hidden rounded-md" role="img" aria-label={label}>
      {counts.map((count, index) => {
        const share = sharePercent(count.count, total);
        return (
          <div
            key={count.label}
            className={`flex items-center justify-center ${colorFor(index)} text-primary-foreground font-mono text-xs font-semibold tabular-nums transition-all duration-500 ease-out`}
            style={{ width: `${total > 0 ? (count.count / total) * 100 : 0}%` }}
            title={`${count.label}: ${count.count.toLocaleString()} (${share}%)`}
          >
            {share >= 8 && `${share}%`}
          </div>
        );
      })}
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
