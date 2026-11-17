// src/components/dashboards/ResolutionComparison.tsx
import type { ResolutionByEscalation } from "@/lib/types/dashboard";
import { formatDuration } from "@/lib/utils";

interface ResolutionComparisonProps {
  data: ResolutionByEscalation[] | undefined;
}

// Fixed colour per group so a missing group never repaints the other.
const GROUPS = [
  { escalated: true, label: "Escalated", solid: "bg-chart-7", light: "bg-chart-7/35" },
  { escalated: false, label: "Not escalated", solid: "bg-chart-1", light: "bg-chart-1/35" },
] as const;

function ticketCount(count: number): string {
  return `${count} ${count === 1 ? "ticket" : "tickets"}`;
}

function formatValue(seconds: number | undefined): string {
  return seconds ? formatDuration(seconds) : "—";
}

export function ResolutionComparison({ data }: ResolutionComparisonProps) {
  const rows = GROUPS.map((group) => ({ ...group, stats: data?.find((r) => r.escalated === group.escalated) }));
  const scaleMax = Math.max(0, ...rows.map((row) => row.stats?.p90 ?? 0));
  const width = (seconds: number | undefined) => (scaleMax > 0 && seconds ? `${(seconds / scaleMax) * 100}%` : "0%");
  const hasData = rows.some((row) => row.stats);

  return (
    <div className="bg-card rounded-xl border p-6">
      <h2 className="text-foreground text-base font-semibold">Resolution: Escalated vs Not Escalated</h2>
      <p className="text-muted-foreground mb-6 text-sm">Tickets opened in this range and since closed</p>

      {hasData ? (
        <>
          <div className="space-y-5">
            {rows.map((row) => (
              <div key={row.label}>
                <div className="mb-2 flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
                  <span className="text-foreground flex items-center gap-2 text-sm font-medium">
                    <span className={`h-2.5 w-2.5 rounded-sm ${row.solid}`} aria-hidden />
                    {row.label}
                    <span className="text-muted-foreground font-normal">({ticketCount(row.stats?.tickets ?? 0)})</span>
                  </span>
                  <span className="text-foreground font-mono text-sm tabular-nums">
                    P50 {formatValue(row.stats?.p50)} · P90 {formatValue(row.stats?.p90)}
                  </span>
                </div>
                <div
                  className="bg-muted relative h-3 overflow-hidden rounded-full"
                  title={`${row.label}: P50 ${formatValue(row.stats?.p50)}, P90 ${formatValue(row.stats?.p90)}`}
                >
                  <div className={`absolute inset-y-0 left-0 rounded-full ${row.light}`} style={{ width: width(row.stats?.p90) }} />
                  <div className={`absolute inset-y-0 left-0 rounded-full ${row.solid}`} style={{ width: width(row.stats?.p50) }} />
                </div>
              </div>
            ))}
          </div>
          <p className="text-muted-foreground mt-4 text-sm">Solid bar is P50, lighter bar is P90, on a shared scale.</p>
        </>
      ) : (
        <p className="text-muted-foreground text-sm">No closed tickets in this range</p>
      )}
    </div>
  );
}
