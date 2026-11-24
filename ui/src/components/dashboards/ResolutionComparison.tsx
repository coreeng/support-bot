// src/components/dashboards/ResolutionComparison.tsx
import type { ResolutionByEscalation } from "@/lib/types/dashboard";
import { formatDuration } from "@/lib/utils";

interface ResolutionComparisonProps {
  data: ResolutionByEscalation[] | undefined;
}

// Fixed colour per group so a missing group never repaints the other.
const GROUPS = [
  { escalated: true, label: "Escalated", color: "bg-chart-7" },
  { escalated: false, label: "Not escalated", color: "bg-chart-1" },
] as const;

const MEASURES = [
  { key: "p50", title: "Median resolution time", tag: "P50" },
  { key: "p90", title: "90th percentile resolution time", tag: "P90" },
] as const;

function ticketCount(count: number): string {
  return `${count} ${count === 1 ? "ticket" : "tickets"}`;
}

function formatValue(seconds: number | undefined): string {
  return seconds == null ? "—" : formatDuration(seconds);
}

export function ResolutionComparison({ data }: ResolutionComparisonProps) {
  const rows = GROUPS.map((group) => ({ ...group, stats: data?.find((r) => r.escalated === group.escalated) }));
  const hasData = rows.some((row) => row.stats);

  return (
    <div className="bg-card rounded-xl border p-6">
      <h2 className="text-foreground mb-4 text-base font-semibold">Resolution: Escalated vs Not Escalated</h2>

      {hasData ? (
        <>
          <div className="mb-6 flex flex-wrap gap-x-6 gap-y-2">
            {rows.map((row) => (
              <span key={row.label} className="text-foreground flex items-center gap-2 text-sm">
                <span className={`h-2.5 w-2.5 rounded-sm ${row.color}`} aria-hidden />
                {row.label}
                <span className="text-muted-foreground">({ticketCount(row.stats?.tickets ?? 0)})</span>
              </span>
            ))}
          </div>

          <div className="space-y-6">
            {MEASURES.map((measure) => {
              // Each question has its own scale: the bars answer it side by side.
              const scaleMax = Math.max(0, ...rows.map((row) => row.stats?.[measure.key] ?? 0));
              return (
                <div key={measure.key}>
                  <p className="text-foreground mb-3 text-sm font-medium">
                    {measure.title} <span className="text-muted-foreground font-normal">({measure.tag})</span>
                  </p>
                  <div className="space-y-2">
                    {rows.map((row) => {
                      const seconds = row.stats?.[measure.key];
                      const width = scaleMax > 0 && seconds ? `${(seconds / scaleMax) * 100}%` : "0%";
                      return (
                        <div
                          key={row.label}
                          className="grid grid-cols-[7rem_1fr_4rem] items-center gap-3"
                          title={`${row.label}, ${measure.tag}: ${formatValue(seconds)}`}
                        >
                          <span className="text-muted-foreground text-sm">{row.label}</span>
                          <div className="bg-muted h-3 overflow-hidden rounded-full">
                            <div className={`h-full rounded-full ${row.color}`} style={{ width }} />
                          </div>
                          <span className="text-foreground text-right font-mono text-sm tabular-nums">{formatValue(seconds)}</span>
                        </div>
                      );
                    })}
                  </div>
                </div>
              );
            })}
          </div>
        </>
      ) : (
        <p className="text-muted-foreground text-sm">No closed tickets in this range</p>
      )}
    </div>
  );
}
