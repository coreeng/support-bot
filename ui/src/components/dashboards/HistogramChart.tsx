// src/components/dashboards/HistogramChart.tsx
import { HistogramBin } from "@/lib/utils";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";

interface HistogramChartProps {
  title: string;
  data: HistogramBin[];
  isLoading?: boolean;
  color?: string;
}

export function HistogramChart({ title, data, isLoading, color = "var(--chart-1)" }: HistogramChartProps) {
  return (
    <div className="bg-card rounded-xl border p-6">
      <h2 className="text-foreground mb-4 text-base font-semibold">{title}</h2>
      {isLoading ? (
        <p className="text-muted-foreground">Loading distribution data...</p>
      ) : data.length > 0 ? (
        <ResponsiveContainer width="100%" height={300}>
          <BarChart data={data}>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
            <XAxis
              dataKey="range"
              angle={-45}
              textAnchor="end"
              height={100}
              interval={0}
              style={{ fontSize: "10px" }}
              stroke="var(--border)"
              tick={{ fill: "var(--muted-foreground)", fontSize: 11 }}
            />
            <YAxis stroke="var(--border)" tick={{ fill: "var(--muted-foreground)", fontSize: 11 }} />
            <Tooltip
              formatter={(value: number) => [value, "Count"]}
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
              labelFormatter={(label) => `Duration: ${label}`}
            />
            <Bar dataKey="count" fill={color} />
          </BarChart>
        </ResponsiveContainer>
      ) : (
        <p className="text-muted-foreground">No distribution data available</p>
      )}
    </div>
  );
}
