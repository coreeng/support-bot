// src/components/dashboards/ResolutionSLASection.tsx
import { formatIncomingVsResolvedSeries } from "@/lib/incomingVsResolved";
import type { IncomingVsResolvedRate } from "@/lib/types/dashboard";
import { formatHoursToDHMS, formatInterval, TimeBucket } from "@/lib/utils";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { RefreshButton } from "./RefreshButton";
import { ResolutionPercentileChart } from "./ResolutionPercentileChart";
import { TimeBucketChart } from "./TimeBucketChart";
import { TimeSeriesChart } from "./TimeSeriesChart";

interface ResolutionSLASectionProps {
  resolutionPercentiles: { p50: number; p75: number; p90: number } | undefined;
  resolutionDurationDistribution: { label: string; count: number; minMinutes: number; maxMinutes: number }[] | undefined;
  resolutionTimesByWeek: { week: string; p50: number; p75: number; p90: number }[] | undefined;
  resolutionTimeByTagInHours: { tag: string; p50: number; p90: number }[] | undefined;
  unresolvedTicketAges: { p50: string; p90: string } | undefined;
  incomingVsResolvedRate: IncomingVsResolvedRate | undefined;
  isResolutionDistributionLoading: boolean;
  isRefreshing: boolean;
  onRefresh: () => void;
}

export function ResolutionSLASection({
  resolutionPercentiles,
  resolutionDurationDistribution,
  resolutionTimesByWeek,
  resolutionTimeByTagInHours,
  unresolvedTicketAges,
  incomingVsResolvedRate,
  isResolutionDistributionLoading,
  isRefreshing,
  onRefresh,
}: ResolutionSLASectionProps) {
  // Convert resolution times by week from seconds to hours
  const resolutionTimesByWeekInHours = resolutionTimesByWeek?.map((week) => ({
    ...week,
    p50: week.p50 / 3600,
    p75: week.p75 / 3600,
    p90: week.p90 / 3600,
  }));
  // Backend provides pre-bucketed data; just map it to the TimeBucket shape.
  const timeBuckets: TimeBucket[] = (resolutionDurationDistribution || []).map((b) => ({
    label: b.label,
    count: b.count,
    minMinutes: b.minMinutes,
    maxMinutes: b.maxMinutes,
  }));
  const formattedIncomingVsResolvedRate = formatIncomingVsResolvedSeries(
    incomingVsResolvedRate?.data ?? [],
    incomingVsResolvedRate?.granularity
  );
  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h2 className="text-foreground text-base font-semibold">Resolution Performance</h2>
          <p className="text-muted-foreground text-sm">Monitor ticket resolution times and trends</p>
        </div>
        <RefreshButton onRefresh={onRefresh} isRefreshing={isRefreshing} />
      </div>

      <div className="mb-6 grid grid-cols-1 gap-6 xl:grid-cols-2">
        {resolutionPercentiles ? (
          <ResolutionPercentileChart p50={resolutionPercentiles.p50} p75={resolutionPercentiles.p75} p90={resolutionPercentiles.p90} />
        ) : (
          <div className="bg-card rounded-xl border p-6">
            <p className="text-muted-foreground text-sm">No data available</p>
          </div>
        )}

        <TimeBucketChart title="Ticket Resolution Duration Distribution" data={timeBuckets} isLoading={isResolutionDistributionLoading} />
      </div>

      <div className="mb-6 grid grid-cols-1 gap-6 xl:grid-cols-2">
        <TimeSeriesChart
          title="Resolution Times by Week (P50/P75/P90)"
          data={resolutionTimesByWeekInHours || []}
          lines={[
            { dataKey: "p50", name: "P50", color: "var(--chart-2)" },
            { dataKey: "p75", name: "P75", color: "var(--chart-3)" },
            { dataKey: "p90", name: "P90", color: "var(--chart-1)" },
          ]}
          tooltipFormatter={(value: number) => [formatHoursToDHMS(value), ""]}
        />

        {/* Resolution Time by Tag */}
        <div className="bg-card rounded-xl border p-6">
          <h2 className="text-foreground mb-4 text-base font-semibold">Resolution Time by Tag (P50/P90) - Hours</h2>
          {resolutionTimeByTagInHours && resolutionTimeByTagInHours.length > 0 ? (
            <ResponsiveContainer width="100%" height={400}>
              <BarChart data={resolutionTimeByTagInHours}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                <XAxis
                  dataKey="tag"
                  angle={-45}
                  textAnchor="end"
                  height={100}
                  stroke="var(--border)"
                  tick={{ fill: "var(--muted-foreground)", fontSize: 11 }}
                />
                <YAxis
                  label={{ value: "Hours", angle: -90, position: "insideLeft" }}
                  stroke="var(--border)"
                  tick={{ fill: "var(--muted-foreground)", fontSize: 11 }}
                />
                <Tooltip
                  formatter={(value: number) => [value.toFixed(2) + " hours", ""]}
                  contentStyle={{
                    background: "var(--popover)",
                    color: "var(--popover-foreground)",
                    border: "1px solid var(--border)",
                    borderRadius: "8px",
                    boxShadow: "0 4px 12px rgba(0,0,0,0.15)",
                  }}
                  labelStyle={{ color: "var(--popover-foreground)" }}
                  itemStyle={{ color: "var(--popover-foreground)" }}
                  cursor={{ stroke: "var(--border)", fill: "var(--accent)" }}
                />
                <Bar dataKey="p50" fill="var(--chart-2)" name="P50 (Median)" />
                <Bar dataKey="p90" fill="var(--chart-1)" name="P90" />
              </BarChart>
            </ResponsiveContainer>
          ) : (
            <p className="text-muted-foreground">No data available</p>
          )}
        </div>

        {/* Unresolved Ticket Ages */}
        <div className="bg-card rounded-xl border p-6">
          <h2 className="text-foreground mb-4 text-base font-semibold">Unresolved Ticket Ages</h2>
          {unresolvedTicketAges ? (
            <div className="grid grid-cols-2 gap-4">
              <div>
                <p className="text-muted-foreground text-sm font-medium">P50 (Median)</p>
                <p className="text-foreground mt-2 font-mono text-2xl font-semibold tracking-tight tabular-nums">
                  {formatInterval(unresolvedTicketAges.p50)}
                </p>
              </div>
              <div>
                <p className="text-muted-foreground text-sm font-medium">P90</p>
                <p className="text-foreground mt-2 font-mono text-2xl font-semibold tracking-tight tabular-nums">
                  {formatInterval(unresolvedTicketAges.p90)}
                </p>
              </div>
            </div>
          ) : (
            <p className="text-muted-foreground text-sm">No data available</p>
          )}
        </div>
      </div>

      {/* Incoming vs Resolved Rate */}
      <div className="grid grid-cols-1 gap-6">
        {formattedIncomingVsResolvedRate.length > 0 ? (
          <TimeSeriesChart
            title="Incoming vs Resolved Rate - Performance SLA"
            data={formattedIncomingVsResolvedRate}
            lines={[
              { dataKey: "incoming", name: "Incoming Queries", color: "var(--chart-5)" },
              { dataKey: "resolved", name: "Resolved Tickets", color: "var(--chart-2)" },
            ]}
            yAxisLabel="Tickets"
            xAxisDataKey="time"
            tooltipFormatter={(value: number) => [`${value} tickets`, ""]}
            showLegend={true}
          />
        ) : (
          <div className="bg-card rounded-xl border p-6">
            <h2 className="text-foreground mb-4 text-base font-semibold">Incoming vs Resolved Rate - Performance SLA</h2>
            <p className="text-muted-foreground">
              {!incomingVsResolvedRate ? "Loading..." : "No data available for the selected date range"}
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
