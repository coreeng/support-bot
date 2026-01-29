// src/components/dashboards/ResponseSLASection.tsx
import { PercentileCard } from './PercentileCard'
import { TimeBucketChart } from './TimeBucketChart'
import { MetricCard } from './MetricCard'
import { RefreshButton } from './RefreshButton'
import { formatHoursToDHMS, createTimeBuckets, TimeBucket } from '@/lib/utils'

interface ResponseSLASectionProps {
    firstResponsePercentiles: { p50: number; p90: number } | undefined
    durationDistribution: number[] | undefined
    unattendedQueries: { count: number } | undefined
    isDistributionLoading: boolean
    isUnattendedLoading: boolean
    isRefreshing: boolean
    onRefresh: () => void
}

export function ResponseSLASection({
    firstResponsePercentiles,
    durationDistribution,
    unattendedQueries,
    isDistributionLoading,
    isUnattendedLoading,
    isRefreshing,
    onRefresh
}: ResponseSLASectionProps) {
    // Create time-based buckets for better visualization
    const timeBuckets: TimeBucket[] = durationDistribution 
        ? createTimeBuckets(durationDistribution)
        : []
    return (
        <div>
            <div className="flex justify-between items-center mb-6">
                <div>
                    <h2 className="text-xl font-bold text-gray-800">Response Performance</h2>
                    <p className="text-sm text-gray-500">Track first response times and unattended queries</p>
                </div>
                <RefreshButton onRefresh={onRefresh} isRefreshing={isRefreshing} />
            </div>
            
            {/* Cards - 50/50 Layout on Large Screens */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                <PercentileCard
                    title="Time to First Response"
                    p50={formatHoursToDHMS((firstResponsePercentiles?.p50 || 0) / 3600)}
                    p90={formatHoursToDHMS((firstResponsePercentiles?.p90 || 0) / 3600)}
                    colorScheme="blue"
                />
                
                <MetricCard
                    title="Total Unattended Queries"
                    value={unattendedQueries?.count ?? 0}
                    description="Queries without a corresponding ticket"
                    isLoading={isUnattendedLoading}
                    colorScheme="orange"
                />
            </div>
            
            <div className="mt-6">
                <TimeBucketChart
                    title="Time to First Response Duration Distribution"
                    data={timeBuckets}
                    isLoading={isDistributionLoading}
                />
            </div>
        </div>
    )
}

