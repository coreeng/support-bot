// src/components/dashboards/ResolutionPercentileChart.tsx
import { formatHoursToDHMS } from "@/lib/utils";
import { Clock } from "lucide-react";

interface ResolutionPercentileChartProps {
  p50: number;
  p75: number;
  p90: number;
}

export function ResolutionPercentileChart({ p50, p75, p90 }: ResolutionPercentileChartProps) {
  // Convert from seconds to hours for calculations
  const p50Hours = p50 / 3600;
  const p75Hours = p75 / 3600;
  const p90Hours = p90 / 3600;
  const maxHours = p90Hours; // Use P90 as the max for scaling

  const percentiles = [
    {
      label: "P50",
      sublabel: "Median",
      value: p50Hours,
      percent: (p50Hours / maxHours) * 100,
      color: "bg-success",
      textColor: "text-success",
    },
    {
      label: "P75",
      sublabel: "75th Percentile",
      value: p75Hours,
      percent: (p75Hours / maxHours) * 100,
      color: "bg-warning",
      textColor: "text-warning",
    },
    { label: "P90", sublabel: "90th Percentile", value: p90Hours, percent: 100, color: "bg-destructive", textColor: "text-destructive" },
  ];

  return (
    <div className="bg-card rounded-xl border p-6">
      <div className="mb-6 flex items-center gap-2">
        <Clock className="text-muted-foreground h-4 w-4" />
        <h2 className="text-foreground text-base font-semibold">Ticket Resolution Durations</h2>
      </div>

      <div className="space-y-6">
        {percentiles.map((p) => (
          <div key={p.label} className="space-y-2">
            <div className="flex items-baseline justify-between">
              <div className="flex items-baseline gap-2">
                <span className={`text-lg font-bold ${p.textColor}`}>{p.label}</span>
                <span className="text-muted-foreground text-sm">{p.sublabel}</span>
              </div>
              <span className="text-foreground font-mono text-2xl font-semibold tracking-tight tabular-nums">
                {formatHoursToDHMS(p.value)}
              </span>
            </div>
            <div className="bg-muted h-2 w-full overflow-hidden rounded-full">
              <div className={`${p.color} h-full rounded-full transition-all duration-500 ease-out`} style={{ width: `${p.percent}%` }} />
            </div>
          </div>
        ))}
      </div>

      <div className="mt-6 border-t pt-4">
        <p className="text-muted-foreground text-center text-xs">
          50% of tickets resolved within {formatHoursToDHMS(p50Hours)} • 90% within {formatHoursToDHMS(p90Hours)}
        </p>
      </div>
    </div>
  );
}
