// src/components/dashboards/HistogramChart.tsx
import { ResponsiveContainer, BarChart, Bar, CartesianGrid, XAxis, YAxis, Tooltip } from 'recharts'
import { HistogramBin } from '@/lib/utils'

interface HistogramChartProps {
    title: string
    data: HistogramBin[]
    isLoading?: boolean
    color?: string
}

export function HistogramChart({ title, data, isLoading, color = '#3B82F6' }: HistogramChartProps) {
    return (
        <div className="bg-white shadow-lg rounded-xl p-6 border border-gray-200">
            <h2 className="text-xl font-semibold text-gray-700 mb-4">
                {title}
            </h2>
            {isLoading ? (
                <p className="text-gray-500">Loading distribution data...</p>
            ) : data.length > 0 ? (
                <ResponsiveContainer width="100%" height={300}>
                    <BarChart data={data}>
                        <CartesianGrid strokeDasharray="3 3" />
                        <XAxis 
                            dataKey="range" 
                            angle={-45}
                            textAnchor="end"
                            height={100}
                            interval={0}
                            style={{ fontSize: '10px' }}
                        />
                        <YAxis />
                        <Tooltip 
                            formatter={(value: number) => [value, 'Count']}
                            labelFormatter={(label) => `Duration: ${label}`}
                        />
                        <Bar dataKey="count" fill={color} />
                    </BarChart>
                </ResponsiveContainer>
            ) : (
                <p className="text-gray-500">No distribution data available</p>
            )}
        </div>
    )
}

