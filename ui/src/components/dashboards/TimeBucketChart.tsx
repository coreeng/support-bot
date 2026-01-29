// src/components/dashboards/TimeBucketChart.tsx
import { ResponsiveContainer, BarChart, Bar, CartesianGrid, XAxis, YAxis, Tooltip, Cell } from 'recharts'
import { TimeBucket } from '@/lib/utils/distribution'

interface TimeBucketChartProps {
    title: string
    data: TimeBucket[]
    isLoading?: boolean
}

export function TimeBucketChart({ title, data, isLoading }: TimeBucketChartProps) {
    // Color coding: green (fast) -> yellow -> orange -> red (slow)
    const getColor = (label: string) => {
        if (label.includes('< 15') || label.includes('15-30')) return '#22c55e'  // green
        if (label.includes('30-60') || label.includes('1-2 hours')) return '#84cc16'  // lime
        if (label.includes('2-4') || label.includes('4-8')) return '#eab308'  // yellow
        if (label.includes('8-24') || label.includes('1-3 days')) return '#f97316'  // orange
        return '#ef4444'  // red for > 3 days
    }

    return (
        <div className="bg-white shadow-lg rounded-xl p-6 border border-gray-200">
            <h2 className="text-xl font-semibold text-gray-700 mb-4">
                {title}
            </h2>
            {isLoading ? (
                <p className="text-gray-500">Loading distribution data...</p>
            ) : data.length > 0 ? (
                <>
                    <ResponsiveContainer width="100%" height={400}>
                        <BarChart data={data}>
                            <CartesianGrid strokeDasharray="3 3" />
                            <XAxis 
                                dataKey="label" 
                                angle={-45}
                                textAnchor="end"
                                height={120}
                                interval={0}
                                style={{ fontSize: '12px' }}
                            />
                            <YAxis label={{ value: 'Ticket Count', angle: -90, position: 'insideLeft' }} />
                            <Tooltip 
                                formatter={(value: number) => [value, 'Tickets']}
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
                <p className="text-gray-500">No distribution data available</p>
            )}
        </div>
    )
}

