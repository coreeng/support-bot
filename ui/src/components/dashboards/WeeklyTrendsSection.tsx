// src/components/dashboards/WeeklyTrendsSection.tsx
import { calculateRunningAverage } from "@/lib/utils";
import { useMemo } from "react";
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { HorizontalBarChart } from "./HorizontalBarChart";
import { RefreshButton } from "./RefreshButton";

interface WeeklyComparisonMetric {
  label: string;
  thisWeek: number;
  lastWeek: number;
  change: number;
}

interface WeeklyTrendsSectionProps {
  weeklyComparison: WeeklyComparisonMetric[] | undefined;
  weeklyTicketCounts: { week: string; opened: number; closed: number; escalated: number; stale: number }[] | undefined;
  topEscalatedTags: { tag: string; count: number }[] | undefined;
  isRefreshing: boolean;
  onRefresh: () => void;
}

export function WeeklyTrendsSection({
  weeklyComparison,
  weeklyTicketCounts,
  topEscalatedTags,
  isRefreshing,
  onRefresh,
}: WeeklyTrendsSectionProps) {
  // Calculate 4-week running averages for smoothed trend lines (separate chart)
  const chartDataWithAvg = useMemo(() => {
    if (!weeklyTicketCounts || weeklyTicketCounts.length === 0) return [];

    return calculateRunningAverage(
      weeklyTicketCounts,
      ["opened", "closed", "escalated", "stale"],
      4 // 4-week rolling average
    );
  }, [weeklyTicketCounts]);

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h2 className="text-foreground text-base font-semibold">Weekly Trends</h2>
          <p className="text-muted-foreground text-sm">Monitor weekly metrics and patterns</p>
        </div>
        <RefreshButton onRefresh={onRefresh} isRefreshing={isRefreshing} />
      </div>

      {/* Weekly Comparison Cards */}
      <div className="mb-6 grid grid-cols-1 gap-6 md:grid-cols-2 xl:grid-cols-4">
        {weeklyComparison?.map((metric) => {
          const isPositive = metric.change >= 0;
          let percentDisplay = "";

          if (metric.lastWeek === 0) {
            if (metric.thisWeek === 0) {
              percentDisplay = "0%";
            } else {
              const percent = metric.thisWeek * 100;
              percentDisplay = `+${percent}%`;
            }
          } else {
            const percentChange = Math.round((metric.change / metric.lastWeek) * 100);
            const sign = percentChange > 0 ? "+" : "";
            percentDisplay = `${sign}${percentChange}%`;
          }

          return (
            <div key={metric.label} className="bg-card rounded-xl border p-6">
              <h3 className="text-foreground mb-2 text-base font-semibold capitalize">{metric.label} This Week</h3>
              <p className="text-foreground font-mono text-3xl font-semibold tracking-tight tabular-nums">{metric.thisWeek}</p>
              <div className="mt-2 flex items-center gap-2">
                <span className="text-primary text-sm font-semibold">
                  {isPositive ? "↑" : "↓"} {Math.abs(metric.change)}
                </span>
                <span className="text-muted-foreground text-xs">({percentDisplay} vs last week)</span>
              </div>
              <p className="text-muted-foreground mt-1 text-xs">Last week: {metric.lastWeek}</p>
            </div>
          );
        })}
      </div>

      <div className="mb-6 grid grid-cols-1 gap-6 xl:grid-cols-2">
        {/* ORIGINAL: Tickets Per Week Breakdown */}
        <div className="bg-card rounded-xl border p-6">
          <h2 className="text-foreground mb-4 text-base font-semibold">Tickets Per Week Breakdown</h2>
          {weeklyTicketCounts && weeklyTicketCounts.length > 0 ? (
            <ResponsiveContainer width="100%" height={400}>
              <LineChart data={weeklyTicketCounts}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                <XAxis
                  dataKey="week"
                  angle={-45}
                  textAnchor="end"
                  height={80}
                  stroke="var(--border)"
                  tick={{ fill: "var(--muted-foreground)", fontSize: 11 }}
                />
                <YAxis stroke="var(--border)" tick={{ fill: "var(--muted-foreground)", fontSize: 11 }} />
                <Tooltip
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
                <Line type="monotone" dataKey="opened" stroke="var(--chart-1)" strokeWidth={2} name="Opened" />
                <Line type="monotone" dataKey="closed" stroke="var(--chart-2)" strokeWidth={2} name="Closed" />
                <Line type="monotone" dataKey="escalated" stroke="var(--chart-4)" strokeWidth={2} name="Escalated" />
                <Line type="monotone" dataKey="stale" stroke="var(--chart-3)" strokeWidth={2} name="Stale" />
              </LineChart>
            </ResponsiveContainer>
          ) : (
            <p className="text-muted-foreground">No data available</p>
          )}
        </div>

        <HorizontalBarChart
          title="Top 10 Tags Escalated This Week"
          data={topEscalatedTags || []}
          dataKey="count"
          yAxisDataKey="tag"
          color="var(--chart-3)"
        />
      </div>

      {/* NEW: Running Average Chart */}
      <div className="mb-6 grid grid-cols-1 gap-6">
        <div className="bg-card rounded-xl border p-6">
          <h2 className="text-foreground mb-4 text-base font-semibold">Running Average Trends</h2>
          {chartDataWithAvg && chartDataWithAvg.length > 0 ? (
            <ResponsiveContainer width="100%" height={450}>
              <LineChart data={chartDataWithAvg}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                <XAxis
                  dataKey="week"
                  angle={-45}
                  textAnchor="end"
                  height={100}
                  tick={{ fontSize: 11, fill: "var(--muted-foreground)" }}
                  stroke="var(--border)"
                />
                <YAxis
                  label={{ value: "Tickets (Avg)", angle: -90, position: "insideLeft" }}
                  stroke="var(--border)"
                  tick={{ fill: "var(--muted-foreground)", fontSize: 11 }}
                />
                <Tooltip
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
                <Legend wrapperStyle={{ paddingTop: "20px" }} iconType="line" />

                {/* Running averages - smooth lines */}
                <Line type="monotone" dataKey="opened_avg" stroke="var(--chart-1)" strokeWidth={3} name="Opened (4-Week Avg)" dot={false} />
                <Line type="monotone" dataKey="closed_avg" stroke="var(--chart-2)" strokeWidth={3} name="Closed (4-Week Avg)" dot={false} />
                <Line
                  type="monotone"
                  dataKey="escalated_avg"
                  stroke="var(--chart-4)"
                  strokeWidth={3}
                  name="Escalated (4-Week Avg)"
                  dot={false}
                />
                <Line type="monotone" dataKey="stale_avg" stroke="var(--chart-3)" strokeWidth={3} name="Stale (4-Week Avg)" dot={false} />
              </LineChart>
            </ResponsiveContainer>
          ) : (
            <p className="text-muted-foreground">No data available</p>
          )}
        </div>
      </div>
    </div>
  );
}
