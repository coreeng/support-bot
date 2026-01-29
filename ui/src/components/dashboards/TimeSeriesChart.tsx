// src/components/dashboards/TimeSeriesChart.tsx
import { ResponsiveContainer, LineChart, Line, CartesianGrid, XAxis, YAxis, Tooltip, Legend } from 'recharts'

interface TimeSeriesChartProps {
    title: string
    data: Record<string, unknown>[]
    lines: {
        dataKey: string
        name: string
        color: string
    }[]
    yAxisLabel?: string
    xAxisDataKey?: string
    tooltipFormatter?: (value: number) => [string, string]
    height?: number
    showLegend?: boolean
}

export function TimeSeriesChart({
    title,
    data,
    lines,
    yAxisLabel = 'Hours',
    xAxisDataKey = 'week',
    tooltipFormatter,
    height = 350,
    showLegend = true
}: TimeSeriesChartProps) {
    return (
        <div className="bg-white shadow-lg rounded-xl p-6 border border-gray-200">
            <h2 className="text-xl font-semibold text-gray-700 mb-4">
                {title}
            </h2>
            {data && data.length > 0 ? (
                <ResponsiveContainer width="100%" height={height}>
                    <LineChart data={data}>
                        <CartesianGrid strokeDasharray="3 3" />
                        <XAxis 
                            dataKey={xAxisDataKey}
                            angle={-45}
                            textAnchor="end"
                            height={100}
                            tick={{ fontSize: 11 }}
                            interval="preserveStartEnd"
                        />
                        <YAxis label={{ value: yAxisLabel, angle: -90, position: 'insideLeft' }} />
                        <Tooltip 
                            formatter={tooltipFormatter}
                        />
                        {showLegend && <Legend />}
                        {lines.map(line => (
                            <Line
                                key={line.dataKey}
                                type="monotone"
                                dataKey={line.dataKey}
                                stroke={line.color}
                                strokeWidth={2}
                                name={line.name}
                            />
                        ))}
                    </LineChart>
                </ResponsiveContainer>
            ) : (
                <p className="text-gray-500">No data available</p>
            )}
        </div>
    )
}

