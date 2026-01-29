// src/lib/utils/distribution.ts

/**
 * Distribution utilities for handling wide-range data with outliers
 */

export interface TimeBucket {
    label: string
    count: number
    minMinutes: number
    maxMinutes: number
}

/**
 * Creates time-based buckets for resolution time distribution
 * Handles ranges from minutes to weeks with clear, meaningful labels
 * Separates extreme outliers for better visualization
 */
export function createTimeBuckets(durationSeconds: number[]): TimeBucket[] {
    if (!durationSeconds || durationSeconds.length === 0) {
        return []
    }

    // Convert to minutes
    const durations = durationSeconds.map(d => d / 60)
    
    // Define meaningful time buckets
    const buckets: TimeBucket[] = [
        { label: '< 15 min', count: 0, minMinutes: 0, maxMinutes: 15 },
        { label: '15-30 min', count: 0, minMinutes: 15, maxMinutes: 30 },
        { label: '30-60 min', count: 0, minMinutes: 30, maxMinutes: 60 },
        { label: '1-2 hours', count: 0, minMinutes: 60, maxMinutes: 120 },
        { label: '2-4 hours', count: 0, minMinutes: 120, maxMinutes: 240 },
        { label: '4-8 hours', count: 0, minMinutes: 240, maxMinutes: 480 },
        { label: '8-24 hours', count: 0, minMinutes: 480, maxMinutes: 1440 },
        { label: '1-3 days', count: 0, minMinutes: 1440, maxMinutes: 4320 },
        { label: '3-7 days', count: 0, minMinutes: 4320, maxMinutes: 10080 },
        { label: '> 7 days', count: 0, minMinutes: 10080, maxMinutes: Infinity },
    ]

    // Count tickets in each bucket
    durations.forEach(duration => {
        for (const bucket of buckets) {
            if (duration >= bucket.minMinutes && duration < bucket.maxMinutes) {
                bucket.count++
                break
            }
        }
    })

    // Filter out empty buckets for cleaner display
    return buckets.filter(b => b.count > 0)
}

/**
 * Creates a percentile summary for quick insights
 */
export interface PercentileSummary {
    p10: number
    p25: number
    p50: number
    p75: number
    p90: number
    p95: number
    p99: number
    min: number
    max: number
    outliers: number  // Count of extreme outliers (>p99)
}

export function calculatePercentiles(durationSeconds: number[]): PercentileSummary | null {
    if (!durationSeconds || durationSeconds.length === 0) {
        return null
    }

    const sorted = [...durationSeconds].sort((a, b) => a - b)
    const n = sorted.length

    const getPercentile = (p: number) => {
        const index = Math.ceil((p / 100) * n) - 1
        return sorted[Math.max(0, Math.min(index, n - 1))] / 60  // Convert to minutes
    }

    const p99 = getPercentile(99)
    const outliers = sorted.filter(d => d / 60 > p99).length

    return {
        p10: getPercentile(10),
        p25: getPercentile(25),
        p50: getPercentile(50),
        p75: getPercentile(75),
        p90: getPercentile(90),
        p95: getPercentile(95),
        p99,
        min: sorted[0] / 60,
        max: sorted[n - 1] / 60,
        outliers
    }
}

