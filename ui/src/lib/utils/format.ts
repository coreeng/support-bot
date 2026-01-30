// src/lib/utils/format.ts

/**
 * Formats a duration in seconds to a human-readable string
 * @param seconds - Duration in seconds
 * @returns Formatted string (e.g., "2.5h", "45m", "30s")
 */
export function formatDuration(seconds: number): string {
    if (seconds >= 3600) {
        return `${(seconds / 3600).toFixed(1)}h`
    } else if (seconds >= 60) {
        return `${Math.round(seconds / 60)}m`
    }
    return `${Math.round(seconds)}s`
}

/**
 * Formats seconds to hours with one decimal place
 * @param seconds - Duration in seconds
 * @returns Hours as a number
 */
export function secondsToHours(seconds: number): number {
    return Math.round((seconds / 3600) * 10) / 10
}

/**
 * Formats a date string to a more readable format
 * @param dateString - ISO date string
 * @returns Formatted date (e.g., "Jan 15, 2025")
 */
export function formatDate(dateString: string): string {
    return new Date(dateString).toLocaleDateString('en-US', {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
    })
}

/**
 * Formats a PostgreSQL interval string to human-readable format
 * Converts to "Xh Ym Zs" or "X days Yh Zm Zs" format
 * @param interval - Interval string (e.g., "1 day 02:30:00.123456" or "20:21:31.44065")
 * @returns Formatted string (e.g., "1 day 2h 30m 0s" or "20h 21m 31s")
 */
export function formatInterval(interval: string): string {
    if (!interval) return ''
    
    // Remove microseconds (anything after the last period in the time portion)
    const cleaned = interval.replace(/\.[\d]+\s*$/, '').trim()
    
    // Extract days if present
    const daysMatch = cleaned.match(/(\d+)\s+days?/)
    const days = daysMatch ? parseInt(daysMatch[1]) : 0
    
    // Extract time portion (HH:MM:SS or H:M:S)
    const timeMatch = cleaned.match(/(\d+):(\d+):(\d+)/)
    if (!timeMatch) return cleaned // Fallback if format unexpected
    
    const hours = parseInt(timeMatch[1])
    const minutes = parseInt(timeMatch[2])
    const seconds = parseInt(timeMatch[3])
    
    // Build readable format
    const parts = []
    if (days > 0) parts.push(`${days} day${days > 1 ? 's' : ''}`)
    if (hours > 0) parts.push(`${hours}h`)
    if (minutes > 0) parts.push(`${minutes}m`)
    if (seconds > 0) parts.push(`${seconds}s`)
    
    // If everything is zero, show "0s"
    if (parts.length === 0) parts.push('0s')
    
    return parts.join(' ')
}

/**
 * Calculates percentage change between two numbers
 * @param current - Current value
 * @param previous - Previous value
 * @returns Formatted percentage string with sign (e.g., "+25%", "-10%", "0%")
 */
export function calculatePercentageChange(current: number, previous: number): string {
    if (previous === 0) {
        if (current === 0) return '0%'
        // Going from 0 to N, show as N * 100%
        const percent = current * 100
        return `+${percent}%`
    }
    
    const percentChange = Math.round((current - previous) / previous * 100)
    const sign = percentChange > 0 ? '+' : ''
    return `${sign}${percentChange}%`
}

