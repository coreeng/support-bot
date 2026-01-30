// src/components/dashboards/ScatterChart.tsx
import { ResponsiveContainer, ScatterChart as RechartsScatter, Scatter, CartesianGrid, XAxis, YAxis, Tooltip } from 'recharts'

interface ScatterChartProps {
    title: string
    data: { index: number; value: number }[]
    yAxisLabel?: string
    color?: string
    isLoading?: boolean
}

export function ScatterChart({ 
    title, 
    data, 
    yAxisLabel = 'Minutes',
    color = '#3B82F6',
    isLoading 
}: ScatterChartProps) {
    return (
        <div className="bg-white shadow-lg rounded-xl p-6 border border-gray-200">
            <h2 className="text-xl font-semibold text-gray-700 mb-4">
                {title}
            </h2>
            {isLoading ? (
                <p className="text-gray-500">Loading distribution data...</p>
            ) : data.length > 0 ? (
                <ResponsiveContainer width="100%" height={300}>
                    <RechartsScatter>
                        <CartesianGrid strokeDasharray="3 3" />
                        <XAxis 
                            dataKey="index" 
                            name="Ticket"
                            label={{ value: 'Ticket Number', position: 'insideBottom', offset: -5 }}
                        />
                        <YAxis 
                            dataKey="value"
                            name={yAxisLabel}
                            label={{ value: yAxisLabel, angle: -90, position: 'insideLeft' }}
                        />
                        <Tooltip 
                            cursor={{ strokeDasharray: '3 3' }}
                            formatter={(value: number) => [value.toFixed(1), yAxisLabel]}
                            labelFormatter={(label) => `Ticket #${label}`}
                        />
                        <Scatter data={data} fill={color} />
                    </RechartsScatter>
                </ResponsiveContainer>
            ) : (
                <p className="text-gray-500">No distribution data available</p>
            )}
        </div>
    )
}

