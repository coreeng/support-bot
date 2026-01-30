// src/components/dashboards/ResolutionSLASection.tsx
import { ResolutionPercentileChart } from './ResolutionPercentileChart'
import { TimeBucketChart } from './TimeBucketChart'
import { TimeSeriesChart } from './TimeSeriesChart'
import { RefreshButton } from './RefreshButton'
import { formatHoursToDHMS, formatInterval, TimeBucket } from '@/lib/utils'
import { ResponsiveContainer, BarChart, Bar, CartesianGrid, XAxis, YAxis, Tooltip } from 'recharts'

interface ResolutionSLASectionProps {
    resolutionPercentiles: { p50: number; p75: number; p90: number } | undefined
    resolutionDurationDistribution: { label: string; count: number; minMinutes: number; maxMinutes: number }[] | undefined
    resolutionTimesByWeek: { week: string; p50: number; p75: number; p90: number }[] | undefined
    resolutionTimeByTagInHours: { tag: string; p50: number; p90: number }[] | undefined
    unresolvedTicketAges: { p50: string; p90: string } | undefined
    incomingVsResolvedRate: { time: string; incoming: number; resolved: number }[] | undefined
    isResolutionDistributionLoading: boolean
    isRefreshing: boolean
    onRefresh: () => void
}

export function ResolutionSLASection({
    resolutionPercentiles,
    resolutionDurationDistribution,
    resolutionTimesByWeek,
    resolutionTimeByTagInHours,
    unresolvedTicketAges,
    incomingVsResolvedRate,
    isResolutionDistributionLoading,
    isRefreshing,
    onRefresh
}: ResolutionSLASectionProps) {
    // Convert resolution times by week from seconds to hours
    const resolutionTimesByWeekInHours = resolutionTimesByWeek?.map(week => ({
        ...week,
        p50: week.p50 / 3600,
        p75: week.p75 / 3600,
        p90: week.p90 / 3600
    }))
    // Create time-based buckets for better visualization
    // Already bucketed by backend; just pass through (fallback to empty)
    const timeBuckets: TimeBucket[] = (resolutionDurationDistribution || []).map(b => ({
        label: b.label,
        count: b.count,
        minMinutes: b.minMinutes,
        maxMinutes: b.maxMinutes,
    }))
    return (
        <div>
            <div className="flex justify-between items-center mb-6">
                <div>
                    <h2 className="text-xl font-bold text-gray-800">Resolution Performance</h2>
                    <p className="text-sm text-gray-500">Monitor ticket resolution times and trends</p>
                </div>
                <RefreshButton onRefresh={onRefresh} isRefreshing={isRefreshing} />
            </div>
            
            <div className="grid grid-cols-1 xl:grid-cols-2 gap-6 mb-6">
                {resolutionPercentiles ? (
                    <ResolutionPercentileChart
                        p50={resolutionPercentiles.p50}
                        p75={resolutionPercentiles.p75}
                        p90={resolutionPercentiles.p90}
                    />
                ) : (
                    <div className="bg-gradient-to-r from-purple-50 to-purple-100 shadow-lg rounded-xl p-6">
                        <p className="text-gray-500">No data available</p>
                    </div>
                )}
                
                <TimeBucketChart
                    title="Ticket Resolution Duration Distribution"
                    data={timeBuckets}
                    isLoading={isResolutionDistributionLoading}
                />
            </div>
            
            <div className="grid grid-cols-1 xl:grid-cols-2 gap-6 mb-6">
                <TimeSeriesChart
                    title="Resolution Times by Week (P50/P75/P90)"
                    data={resolutionTimesByWeekInHours || []}
                    lines={[
                        { dataKey: 'p50', name: 'P50', color: '#22c55e' },
                        { dataKey: 'p75', name: 'P75', color: '#f59e0b' },
                        { dataKey: 'p90', name: 'P90', color: '#3b82f6' }
                    ]}
                    tooltipFormatter={(value: number) => [formatHoursToDHMS(value), '']}
                />
                
                {/* Resolution Time by Tag */}
                <div className="bg-white shadow-lg rounded-xl p-6 border border-gray-200">
                    <h2 className="text-xl font-semibold text-gray-700 mb-4">
                        Resolution Time by Tag (P50/P90) - Hours
                    </h2>
                    {resolutionTimeByTagInHours && resolutionTimeByTagInHours.length > 0 ? (
                        <ResponsiveContainer width="100%" height={400}>
                            <BarChart data={resolutionTimeByTagInHours}>
                                <CartesianGrid strokeDasharray="3 3" />
                                <XAxis 
                                    dataKey="tag" 
                                    angle={-45}
                                    textAnchor="end"
                                    height={100}
                                />
                                <YAxis label={{ value: 'Hours', angle: -90, position: 'insideLeft' }} />
                                <Tooltip 
                                    formatter={(value: number) => [value.toFixed(2) + ' hours', '']}
                                />
                                <Bar dataKey="p50" fill="#22c55e" name="P50 (Median)" />
                                <Bar dataKey="p90" fill="#3b82f6" name="P90" />
                            </BarChart>
                        </ResponsiveContainer>
                    ) : (
                        <p className="text-gray-500">No data available</p>
                    )}
                </div>
                
                {/* Unresolved Ticket Ages */}
                <div className="bg-gradient-to-r from-cyan-50 to-cyan-100 border border-cyan-200 rounded-xl p-6">
                    <h2 className="text-xl font-semibold text-gray-700 mb-4">
                        Unresolved Ticket Ages
                    </h2>
                    {unresolvedTicketAges ? (
                        <div className="flex justify-around">
                            <div className="text-center">
                                <p className="text-sm font-medium text-cyan-600">P50 (Median)</p>
                                <p className="text-2xl font-bold text-cyan-800">{formatInterval(unresolvedTicketAges.p50)}</p>
                            </div>
                            <div className="text-center">
                                <p className="text-sm font-medium text-cyan-600">P90</p>
                                <p className="text-2xl font-bold text-cyan-800">{formatInterval(unresolvedTicketAges.p90)}</p>
                            </div>
                        </div>
                    ) : (
                        <p className="text-gray-500">No data available</p>
                    )}
                </div>
            </div>
            
            {/* Incoming vs Resolved Rate */}
            <div className="grid grid-cols-1 gap-6">
                {incomingVsResolvedRate && incomingVsResolvedRate.length > 0 ? (
                    <TimeSeriesChart
                        title="Incoming vs Resolved Rate - Performance SLA"
                        data={incomingVsResolvedRate.map(item => {
                            const date = new Date(item.time)
                            // Always show hourly labels for detailed tracking
                            const timeLabel = date.toLocaleString('en-US', { 
                                month: 'short', 
                                day: 'numeric',
                                year: 'numeric',
                                hour: 'numeric'
                            })
                            return {
                                time: timeLabel,
                                incoming: item.incoming,
                                resolved: item.resolved
                            }
                        })}
                        lines={[
                            { dataKey: 'incoming', name: 'Incoming Queries', color: '#ef4444' },
                            { dataKey: 'resolved', name: 'Resolved Tickets', color: '#22c55e' }
                        ]}
                        yAxisLabel="Tickets"
                        xAxisDataKey="time"
                        tooltipFormatter={(value: number) => [`${value} tickets`, '']}
                        showLegend={true}
                    />
                ) : (
                    <div className="bg-white shadow-lg rounded-xl p-6 border border-gray-200">
                        <h2 className="text-xl font-semibold text-gray-700 mb-4">
                            Incoming vs Resolved Rate - Performance SLA
                        </h2>
                        <p className="text-gray-500">
                            {!incomingVsResolvedRate ? 'Loading...' : 'No data available for the selected date range'}
                        </p>
                    </div>
                )}
            </div>
        </div>
    )
}

