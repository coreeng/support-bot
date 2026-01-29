// src/components/dashboards/WeeklyTrendsSection.tsx
import { HorizontalBarChart } from './HorizontalBarChart'
import { RefreshButton } from './RefreshButton'
import { ResponsiveContainer, LineChart, Line, CartesianGrid, XAxis, YAxis, Tooltip, Legend } from 'recharts'
import { calculateRunningAverage } from '@/lib/utils'
import { useMemo } from 'react'

interface WeeklyComparisonMetric {
    label: string
    thisWeek: number
    lastWeek: number
    change: number
}

interface WeeklyTrendsSectionProps {
    weeklyComparison: WeeklyComparisonMetric[] | undefined
    weeklyTicketCounts: { week: string; opened: number; closed: number; escalated: number; stale: number }[] | undefined
    topEscalatedTags: { tag: string; count: number }[] | undefined
    isRefreshing: boolean
    onRefresh: () => void
}

export function WeeklyTrendsSection({
    weeklyComparison,
    weeklyTicketCounts,
    topEscalatedTags,
    isRefreshing,
    onRefresh
}: WeeklyTrendsSectionProps) {
    // Calculate 4-week running averages for smoothed trend lines (separate chart)
    const chartDataWithAvg = useMemo(() => {
        if (!weeklyTicketCounts || weeklyTicketCounts.length === 0) return []
        
        return calculateRunningAverage(
            weeklyTicketCounts,
            ['opened', 'closed', 'escalated', 'stale'],
            4 // 4-week rolling average
        )
    }, [weeklyTicketCounts])
    
    return (
        <div>
            <div className="flex justify-between items-center mb-6">
                <div>
                    <h2 className="text-xl font-bold text-gray-800">Weekly Trends</h2>
                    <p className="text-sm text-gray-500">Monitor weekly metrics and patterns</p>
                </div>
                <RefreshButton onRefresh={onRefresh} isRefreshing={isRefreshing} />
            </div>
            
            {/* Weekly Comparison Cards */}
            <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-6 mb-6">
                    {weeklyComparison?.map((metric) => {
                        const isPositive = metric.change >= 0
                        let percentDisplay = ''
                        
                        if (metric.lastWeek === 0) {
                            if (metric.thisWeek === 0) {
                                percentDisplay = '0%'
                            } else {
                                const percent = metric.thisWeek * 100
                                percentDisplay = `+${percent}%`
                            }
                        } else {
                            const percentChange = Math.round((metric.change / metric.lastWeek) * 100)
                            const sign = percentChange > 0 ? '+' : ''
                            percentDisplay = `${sign}${percentChange}%`
                        }
                        
                        return (
                            <div key={metric.label} className="bg-white shadow-lg rounded-xl p-6 border border-gray-200">
                                <h3 className="text-sm font-medium text-gray-600 capitalize mb-2">
                                    {metric.label} This Week
                                </h3>
                                <p className="text-3xl font-bold text-gray-800">{metric.thisWeek}</p>
                                <div className="mt-2 flex items-center gap-2">
                                    <span className="text-sm font-semibold text-blue-600">
                                        {isPositive ? '↑' : '↓'} {Math.abs(metric.change)}
                                    </span>
                                    <span className="text-xs text-gray-500">
                                        ({percentDisplay} vs last week)
                                    </span>
                                </div>
                                <p className="text-xs text-gray-400 mt-1">Last week: {metric.lastWeek}</p>
                            </div>
                        )
                    })}
            </div>

            <div className="grid grid-cols-1 xl:grid-cols-2 gap-6 mb-6">
                {/* ORIGINAL: Tickets Per Week Breakdown */}
                <div className="bg-white shadow-lg rounded-xl p-6 border border-gray-200">
                    <h2 className="text-xl font-semibold text-gray-700 mb-4">
                        Tickets Per Week Breakdown
                    </h2>
                    {weeklyTicketCounts && weeklyTicketCounts.length > 0 ? (
                        <ResponsiveContainer width="100%" height={400}>
                            <LineChart data={weeklyTicketCounts}>
                                <CartesianGrid strokeDasharray="3 3" />
                                <XAxis 
                                    dataKey="week" 
                                    angle={-45}
                                    textAnchor="end"
                                    height={80}
                                />
                                <YAxis />
                                <Tooltip />
                                <Line type="monotone" dataKey="opened" stroke="#3b82f6" strokeWidth={2} name="Opened" />
                                <Line type="monotone" dataKey="closed" stroke="#22c55e" strokeWidth={2} name="Closed" />
                                <Line type="monotone" dataKey="escalated" stroke="#a855f7" strokeWidth={2} name="Escalated" />
                                <Line type="monotone" dataKey="stale" stroke="#f59e0b" strokeWidth={2} name="Stale" />
                            </LineChart>
                        </ResponsiveContainer>
                    ) : (
                        <p className="text-gray-500">No data available</p>
                    )}
                </div>

                <HorizontalBarChart
                    title="Top 10 Tags Escalated This Week"
                    data={topEscalatedTags || []}
                    dataKey="count"
                    yAxisDataKey="tag"
                    color="#f59e0b"
                />
            </div>
            
            {/* NEW: Running Average Chart */}
            <div className="grid grid-cols-1 gap-6 mb-6">
                <div className="bg-white shadow-lg rounded-xl p-6 border border-gray-200">
                    <h2 className="text-xl font-semibold text-gray-700 mb-4">
                        Running Average Trends
                    </h2>
                    {chartDataWithAvg && chartDataWithAvg.length > 0 ? (
                        <ResponsiveContainer width="100%" height={450}>
                            <LineChart data={chartDataWithAvg}>
                                <CartesianGrid strokeDasharray="3 3" />
                                <XAxis 
                                    dataKey="week" 
                                    angle={-45}
                                    textAnchor="end"
                                    height={100}
                                    tick={{ fontSize: 11 }}
                                />
                                <YAxis label={{ value: 'Tickets (Avg)', angle: -90, position: 'insideLeft' }} />
                                <Tooltip />
                                <Legend 
                                    wrapperStyle={{ paddingTop: '20px' }}
                                    iconType="line"
                                />
                                
                                {/* Running averages - smooth lines */}
                                <Line 
                                    type="monotone" 
                                    dataKey="opened_avg" 
                                    stroke="#3b82f6" 
                                    strokeWidth={3} 
                                    name="Opened (4-Week Avg)" 
                                    dot={false}
                                />
                                <Line 
                                    type="monotone" 
                                    dataKey="closed_avg" 
                                    stroke="#22c55e" 
                                    strokeWidth={3} 
                                    name="Closed (4-Week Avg)" 
                                    dot={false}
                                />
                                <Line 
                                    type="monotone" 
                                    dataKey="escalated_avg" 
                                    stroke="#a855f7" 
                                    strokeWidth={3} 
                                    name="Escalated (4-Week Avg)" 
                                    dot={false}
                                />
                                <Line 
                                    type="monotone" 
                                    dataKey="stale_avg" 
                                    stroke="#f59e0b" 
                                    strokeWidth={3} 
                                    name="Stale (4-Week Avg)" 
                                    dot={false}
                                />
                            </LineChart>
                        </ResponsiveContainer>
                    ) : (
                        <p className="text-gray-500">No data available</p>
                    )}
                </div>
            </div>
        </div>
    )
}

