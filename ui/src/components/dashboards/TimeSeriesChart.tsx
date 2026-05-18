// src/components/dashboards/TimeSeriesChart.tsx
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { NameType, Props as TooltipContentProps, ValueType } from "recharts/types/component/DefaultTooltipContent";

interface TimeSeriesChartProps<V extends ValueType = ValueType, N extends NameType = NameType> {
  title: string;
  data: Record<string, unknown>[];
  lines: {
    dataKey: string;
    name: string;
    color: string;
  }[];
  yAxisLabel?: string;
  xAxisDataKey?: string;
  tooltipFormatter?: TooltipContentProps<V, N>["formatter"];
  height?: number;
  showLegend?: boolean;
  emptyMessage?: string;
}

export function TimeSeriesChart<V extends ValueType = ValueType, N extends NameType = NameType>({
  title,
  data,
  lines,
  yAxisLabel = "Hours",
  xAxisDataKey = "week",
  tooltipFormatter,
  height = 350,
  showLegend = true,
  emptyMessage = "No data available",
}: TimeSeriesChartProps<V, N>) {
  return (
    <div className="bg-card rounded-xl border p-6">
      <h3 className="text-foreground mb-4 text-base font-semibold">{title}</h3>
      {data && data.length > 0 ? (
        <ResponsiveContainer width="100%" height={height}>
          <LineChart data={data}>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
            <XAxis
              dataKey={xAxisDataKey}
              angle={-45}
              textAnchor="end"
              height={100}
              tick={{ fontSize: 11, fill: "var(--muted-foreground)" }}
              interval="preserveStartEnd"
              stroke="var(--border)"
            />
            <YAxis
              label={{ value: yAxisLabel, angle: -90, position: "insideLeft" }}
              stroke="var(--border)"
              tick={{ fill: "var(--muted-foreground)", fontSize: 11 }}
            />
            <Tooltip
              formatter={tooltipFormatter}
              contentStyle={{
                background: "var(--popover)",
                color: "var(--popover-foreground)",
                border: "1px solid var(--border)",
                borderRadius: "8px",
                boxShadow: "0 4px 12px rgba(0,0,0,0.15)",
              }}
              labelStyle={{ color: "var(--popover-foreground)" }}
              itemStyle={{ color: "var(--popover-foreground)" }}
              cursor={{ stroke: "var(--border)" }}
            />
            {showLegend && <Legend />}
            {lines.map((line) => (
              <Line key={line.dataKey} type="monotone" dataKey={line.dataKey} stroke={line.color} strokeWidth={2} name={line.name} />
            ))}
          </LineChart>
        </ResponsiveContainer>
      ) : (
        <p className="text-muted-foreground">{emptyMessage}</p>
      )}
    </div>
  );
}
