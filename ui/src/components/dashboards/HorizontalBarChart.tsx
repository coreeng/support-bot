// src/components/dashboards/HorizontalBarChart.tsx
import { ResponsiveContainer, BarChart, Bar, CartesianGrid, XAxis, YAxis, Tooltip } from 'recharts'

interface HorizontalBarChartProps {
    title: string
    data: Record<string, unknown>[]
    dataKey: string
    yAxisDataKey: string
    color: string
    tooltipFormatter?: (value: number) => [string, string]
    height?: number
}

export function HorizontalBarChart({
    title,
    data,
    dataKey,
    yAxisDataKey,
    color,
    tooltipFormatter,
    height = 350
}: HorizontalBarChartProps) {
    return (
        <div className="bg-white shadow-lg rounded-xl p-6 border border-gray-200">
            <h2 className="text-xl font-semibold text-gray-700 mb-4">
                {title}
            </h2>
            {data && data.length > 0 ? (
                <ResponsiveContainer width="100%" height={height}>
                    <BarChart data={data} layout="vertical">
                        <CartesianGrid strokeDasharray="3 3" />
                        <XAxis type="number" />
                        <YAxis 
                            dataKey={yAxisDataKey} 
                            type="category" 
                            width={150}
                            style={{ fontSize: '12px' }}
                        />
                        <Tooltip formatter={tooltipFormatter} />
                        <Bar dataKey={dataKey} fill={color} />
                    </BarChart>
                </ResponsiveContainer>
            ) : (
                <p className="text-gray-500">No data available</p>
            )}
        </div>
    )
}

