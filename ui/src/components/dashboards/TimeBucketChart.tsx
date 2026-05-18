// src/components/dashboards/TimeBucketChart.tsx
import { TimeBucket } from "@/lib/utils/distribution";
import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";

interface TimeBucketChartProps {
  title: string;
  data: TimeBucket[];
  isLoading?: boolean;
}

export function TimeBucketChart({ title, data, isLoading }: TimeBucketChartProps) {
  // Color coding: green (fast) -> yellow -> orange -> red (slow)
  const getColor = (label: string) => {
    if (label.includes("< 15") || label.includes("15-30")) return "var(--chart-2)"; // green
    if (label.includes("30-60") || label.includes("1-2 hours")) return "var(--chart-11)"; // lime
    if (label.includes("2-4") || label.includes("4-8")) return "var(--chart-3)"; // amber
    if (label.includes("8-24") || label.includes("1-3 days")) return "var(--chart-7)"; // orange
    return "var(--chart-5)"; // red for > 3 days
  };

  return (
    <div className="bg-card rounded-xl border p-6">
      <h2 className="text-foreground mb-4 text-base font-semibold">{title}</h2>
      {isLoading ? (
        <p className="text-muted-foreground">Loading distribution data...</p>
      ) : data.length > 0 ? (
        <>
          <ResponsiveContainer width="100%" height={400}>
            <BarChart data={data}>
              <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
              <XAxis
                dataKey="label"
                angle={-45}
                textAnchor="end"
                height={120}
                interval={0}
                style={{ fontSize: "12px" }}
                stroke="var(--border)"
                tick={{ fill: "var(--muted-foreground)", fontSize: 11 }}
              />
              <YAxis
                label={{ value: "Ticket Count", angle: -90, position: "insideLeft" }}
                stroke="var(--border)"
                tick={{ fill: "var(--muted-foreground)", fontSize: 11 }}
              />
              <Tooltip
                formatter={(value: number) => [value, "Tickets"]}
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
                labelFormatter={(label) => `Time Range: ${label}`}
              />
              <Bar dataKey="count">
                {data.map((entry, index) => (
                  <Cell key={`cell-${index}`} fill={getColor(entry.label)} />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </>
      ) : (
        <p className="text-muted-foreground">No distribution data available</p>
      )}
    </div>
  );
}
