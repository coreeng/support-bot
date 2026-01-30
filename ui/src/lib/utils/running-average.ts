// src/lib/utils/running-average.ts

/**
 * Calculate running average (moving average) for a dataset
 * @param data Array of objects with numeric values
 * @param keys Keys to calculate running average for
 * @param windowSize Number of periods to average (default: 4)
 * @returns New array with original data plus running average fields
 */
export function calculateRunningAverage<T extends Record<string, unknown>>(
    data: T[],
    keys: (keyof T)[],
    windowSize: number = 4
): (T & Record<string, number>)[] {
    if (!data || data.length === 0) return []
    
    return data.map((item, index) => {
        // Create a mutable copy using Record to allow dynamic property assignment
        const result: Record<string, unknown> = { ...item }
        
        keys.forEach(key => {
            // Calculate average of last N items (including current)
            const startIndex = Math.max(0, index - windowSize + 1)
            const windowData = data.slice(startIndex, index + 1)
            
            const sum = windowData.reduce((acc, curr) => {
                const value = curr[key]
                return acc + (typeof value === 'number' ? value : 0)
            }, 0)
            
            const avg = sum / windowData.length
            
            // Add running average with suffix
            result[`${String(key)}_avg`] = Math.round(avg * 100) / 100 // Round to 2 decimals
        })
        
        return result as T & Record<string, number>
    })
}

