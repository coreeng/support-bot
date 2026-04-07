import { formatTimeBucketLabel, type TimeBucketResolution } from '@/lib/dateRange'
import type { IncomingVsResolvedGranularity, IncomingVsResolvedRatePoint } from '@/lib/types/dashboard'

export function timeBucketResolutionForIncomingVsResolvedGranularity(
    granularity?: IncomingVsResolvedGranularity
): TimeBucketResolution {
    switch (granularity) {
        case 'HOUR':
            return 'hour'
        case 'WEEK':
            return 'week'
        case 'DAY':
        default:
            return 'day'
    }
}

export function formatIncomingVsResolvedSeries(
    points: IncomingVsResolvedRatePoint[],
    granularity?: IncomingVsResolvedGranularity
): IncomingVsResolvedRatePoint[] {
    const resolution = timeBucketResolutionForIncomingVsResolvedGranularity(granularity)

    return points.map(point => ({
        ...point,
        time: formatTimeBucketLabel(point.time, resolution),
    }))
}