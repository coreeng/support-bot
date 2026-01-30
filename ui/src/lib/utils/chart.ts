// src/lib/utils/chart.ts

/**
 * Chart utility functions for histogram generation, binning, and data transformations
 */

export interface HistogramBin {
    range: string
    count: number
}

/**
 * Generates histogram data from an array of durations
 * Automatically selects appropriate unit (seconds, minutes, or hours) based on median
 * Uses Freedman-Diaconis rule for optimal bin width
 */
export function generateHistogram(durations: number[]): HistogramBin[] {
    if (!durations || durations.length === 0) {
        return []
    }

    // Sort durations for percentile calculations
    const sorted = [...durations].sort((a, b) => a - b)
    // const median = sorted[Math.floor(sorted.length / 2)] // Unused for now

    // Select unit based on max duration for better readability
    const maxDuration = sorted[sorted.length - 1]
    let unit: 'seconds' | 'minutes' | 'hours'
    let divisor: number
    let unitLabel: string

    if (maxDuration < 120) {
        // All data under 2 minutes: use seconds
        unit = 'seconds'
        divisor = 1
        unitLabel = 's'
    } else if (maxDuration < 86400) {
        // Max under 24 hours: use minutes (not hours for better granularity)
        unit = 'minutes'
        divisor = 60
        unitLabel = 'm'
    } else {
        // Max 24+ hours: use hours
        unit = 'hours'
        divisor = 3600
        unitLabel = 'h'
    }

    // Convert durations to selected unit
    const values = sorted.map(d => d / divisor)
    const min = values[0]
    const max = values[values.length - 1]

    // Calculate optimal bin width using Freedman-Diaconis rule
    const q1 = values[Math.floor(values.length * 0.25)]
    const q3 = values[Math.floor(values.length * 0.75)]
    const iqr = q3 - q1
    const binWidth = (2 * iqr) / Math.pow(values.length, 1 / 3)

    // Calculate number of bins
    const numBins = Math.max(5, Math.min(20, Math.ceil((max - min) / binWidth)))

    // Create bins
    const actualBinWidth = (max - min) / numBins
    const bins: HistogramBin[] = []

    for (let i = 0; i < numBins; i++) {
        const binMin = min + i * actualBinWidth
        const binMax = min + (i + 1) * actualBinWidth

        const count = values.filter(v => v >= binMin && v < binMax).length

        // Only include non-empty bins
        if (count > 0) {
            // Use appropriate precision based on unit and data range
            let precision = 0
            if (unit === 'hours') {
                precision = 1
            } else if (unit === 'minutes' && (max - min) < 10) {
                precision = 1  // Use decimals for small minute ranges
            }
            
            bins.push({
                range: `${binMin.toFixed(precision)}-${binMax.toFixed(precision)}${unitLabel}`,
                count
            })
        }
    }

    return bins
}

/**
 * Converts seconds to hours with specified precision
 */
export function secondsToHoursForChart(seconds: number, precision: number = 1): number {
    return Math.round((seconds / 3600) * Math.pow(10, precision)) / Math.pow(10, precision)
}

/**
 * Formats a week string (ISO format) to a more readable format
 */
export function formatWeek(weekString: string): string {
    const date = new Date(weekString)
    return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
}

/**
 * Formats duration in hours to a human-readable string
 */
export function formatHoursToDHMS(hours: number): string {
    if (!hours || isNaN(hours)) return '0:00:00'
    const totalSeconds = Math.round(hours * 3600)
    const d = Math.floor(totalSeconds / 86400)
    const h = Math.floor((totalSeconds % 86400) / 3600)
    const m = Math.floor((totalSeconds % 3600) / 60)
    const s = totalSeconds % 60
    return `${d}d ${h}h ${m}m ${s}s`
}

/**
 * Transforms resolution time by tag data for charting (converts seconds to hours)
 */
export function transformResolutionTimeByTag(data: { tag: string; p50: number; p90: number }[]) {
    return data.map(item => ({
        tag: item.tag,
        p50: secondsToHoursForChart(item.p50),
        p90: secondsToHoursForChart(item.p90)
    }))
}

