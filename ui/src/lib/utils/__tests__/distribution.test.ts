// src/lib/utils/__tests__/distribution.test.ts
import { createTimeBuckets } from '../distribution'

describe('createTimeBuckets', () => {
    it('should categorize durations into correct time buckets', () => {
        const durations = [
            30,      // < 15 min (0.5 min)
            600,     // < 15 min (10 min)
            1800,    // 30-60 min (30 min)
            3600,    // 1-2 hours (60 min)
            7200,    // 2-4 hours (120 min)
            14400,   // 4-8 hours (240 min)
            43200,   // 8-24 hours (720 min)
            86400,   // 1-3 days (1440 min)
            259200,  // 3-7 days (4320 min)
            604800   // > 7 days (10080 min)
        ]
        
        const result = createTimeBuckets(durations)
        
        // Should have 9 buckets (two items in < 15 min bucket)
        expect(result.length).toBe(9)
        
        // Check labels exist
        expect(result.find(b => b.label === '< 15 min')).toBeDefined()
        expect(result.find(b => b.label === '> 7 days')).toBeDefined()
        
        // First bucket should have 2 items
        const firstBucket = result.find(b => b.label === '< 15 min')
        expect(firstBucket?.count).toBe(2)
    })

    it('should count multiple items in same bucket', () => {
        const durations = [30, 60, 90, 120] // All < 15 min
        const result = createTimeBuckets(durations)
        
        const firstBucket = result.find(b => b.label === '< 15 min')
        expect(firstBucket?.count).toBe(4)
    })

    it('should handle empty array', () => {
        const result = createTimeBuckets([])
        
        // Should return empty array (filters out empty buckets)
        expect(result.length).toBe(0)
        expect(result).toEqual([])
    })

    it('should handle edge cases on boundaries', () => {
        const durations = [
            899,    // Just under 15 min (< 15 min bucket)
            900,    // Exactly 15 min (15-30 min bucket)
            1799,   // Just under 30 min (15-30 min bucket)
            1800,   // Exactly 30 min (30-60 min bucket)
        ]
        
        const result = createTimeBuckets(durations)
        
        const under15 = result.find(b => b.label === '< 15 min')
        const between15and30 = result.find(b => b.label === '15-30 min')
        const between30and60 = result.find(b => b.label === '30-60 min')
        
        expect(under15?.count).toBe(1)
        expect(between15and30?.count).toBe(2)
        expect(between30and60?.count).toBe(1)
    })

    it('should maintain bucket order', () => {
        const durations = [604800, 30, 3600] // Random order: > 7 days, < 15 min, 1-2 hours
        const result = createTimeBuckets(durations)
        
        // Buckets should always be in time order (< 15 min, 1-2 hours, > 7 days)
        expect(result.length).toBe(3)
        expect(result[0].label).toBe('< 15 min')
        expect(result[1].label).toBe('1-2 hours')
        expect(result[2].label).toBe('> 7 days')
    })

    it('should handle very large durations', () => {
        const durations = [1000000, 2000000] // Very large values
        const result = createTimeBuckets(durations)
        
        const largestBucket = result.find(b => b.label === '> 7 days')
        expect(largestBucket?.count).toBe(2)
    })

    it('should only include non-empty buckets', () => {
        const durations = [60] // Only one in < 15 min (1 minute)
        const result = createTimeBuckets(durations)
        
        // Should only return buckets with data
        expect(result.length).toBe(1)
        expect(result[0].label).toBe('< 15 min')
        expect(result[0].count).toBe(1)
    })
})

