// src/lib/utils/__tests__/running-average.test.ts
/* eslint-disable @typescript-eslint/no-explicit-any */
import { calculateRunningAverage } from '../running-average'

describe('calculateRunningAverage', () => {
    describe('basic functionality', () => {
        it('should calculate running average for a single key', () => {
            const data = [
                { week: '2024-01-01', value: 10 },
                { week: '2024-01-08', value: 20 },
                { week: '2024-01-15', value: 30 },
                { week: '2024-01-22', value: 40 },
            ]

            const result = calculateRunningAverage(data, ['value'], 2)

            expect(result).toEqual([
                { week: '2024-01-01', value: 10, value_avg: 10 },      // avg(10) = 10
                { week: '2024-01-08', value: 20, value_avg: 15 },      // avg(10, 20) = 15
                { week: '2024-01-15', value: 30, value_avg: 25 },      // avg(20, 30) = 25
                { week: '2024-01-22', value: 40, value_avg: 35 },      // avg(30, 40) = 35
            ])
        })

        it('should calculate running average for multiple keys', () => {
            const data = [
                { week: '2024-01-01', opened: 10, closed: 8 },
                { week: '2024-01-08', opened: 20, closed: 15 },
                { week: '2024-01-15', opened: 30, closed: 25 },
            ]

            const result = calculateRunningAverage(data, ['opened', 'closed'], 2)

            expect(result).toHaveLength(3)
            expect(result[0]).toMatchObject({ opened: 10, closed: 8, opened_avg: 10, closed_avg: 8 })
            expect(result[1]).toMatchObject({ opened: 20, closed: 15, opened_avg: 15, closed_avg: 11.5 })
            expect(result[2]).toMatchObject({ opened: 30, closed: 25, opened_avg: 25, closed_avg: 20 })
        })

        it('should use default window size of 4', () => {
            const data = [
                { value: 10 },
                { value: 20 },
                { value: 30 },
                { value: 40 },
                { value: 50 },
            ]

            const result = calculateRunningAverage(data, ['value'])

            // At index 4: avg of last 4 values (20, 30, 40, 50) = 35
            expect(result[4].value_avg).toBe(35)
        })
    })

    describe('window size behavior', () => {
        it('should handle window size of 1 (no smoothing)', () => {
            const data = [
                { value: 10 },
                { value: 25 },
                { value: 15 },
            ]

            const result = calculateRunningAverage(data, ['value'], 1)

            // Window of 1 means each value equals its own average
            expect(result[0].value_avg).toBe(10)
            expect(result[1].value_avg).toBe(25)
            expect(result[2].value_avg).toBe(15)
        })

        it('should handle window size larger than data length', () => {
            const data = [
                { value: 10 },
                { value: 20 },
                { value: 30 },
            ]

            const result = calculateRunningAverage(data, ['value'], 10)

            // Should average all available data up to current point
            expect(result[0].value_avg).toBe(10)           // avg(10)
            expect(result[1].value_avg).toBe(15)           // avg(10, 20)
            expect(result[2].value_avg).toBe(20)           // avg(10, 20, 30)
        })

        it('should properly slide the window', () => {
            const data = [
                { value: 100 },
                { value: 200 },
                { value: 300 },
                { value: 400 },
                { value: 500 },
            ]

            const result = calculateRunningAverage(data, ['value'], 3)

            expect(result[0].value_avg).toBe(100)          // avg(100)
            expect(result[1].value_avg).toBe(150)          // avg(100, 200)
            expect(result[2].value_avg).toBe(200)          // avg(100, 200, 300)
            expect(result[3].value_avg).toBe(300)          // avg(200, 300, 400) - window slides
            expect(result[4].value_avg).toBe(400)          // avg(300, 400, 500)
        })
    })

    describe('edge cases', () => {
        it('should return empty array for empty input', () => {
            const result = calculateRunningAverage([], ['value'])
            expect(result).toEqual([])
        })

        it('should return empty array for null input', () => {
            const result = calculateRunningAverage(null as any, ['value'])
            expect(result).toEqual([])
        })

        it('should return empty array for undefined input', () => {
            const result = calculateRunningAverage(undefined as any, ['value'])
            expect(result).toEqual([])
        })

        it('should handle missing keys gracefully', () => {
            const data = [
                { value: 10 },
                { other: 20 },  // missing 'value'
                { value: 30 },
            ]

            const result = calculateRunningAverage(data, ['value'], 2)

            // Missing values should be treated as 0
            expect(result[1].value_avg).toBe(5)  // avg(10, 0)
            expect(result[2].value_avg).toBe(15) // avg(0, 30)
        })

        it('should handle non-numeric values as 0', () => {
            const data = [
                { value: 10 },
                { value: 'invalid' as any },
                { value: 30 },
            ]

            const result = calculateRunningAverage(data, ['value'], 2)

            expect(result[1].value_avg).toBe(5)  // avg(10, 0)
            expect(result[2].value_avg).toBe(15) // avg(0, 30)
        })

        it('should preserve non-averaged fields', () => {
            const data = [
                { week: '2024-01-01', value: 10, label: 'First' },
                { week: '2024-01-08', value: 20, label: 'Second' },
            ]

            const result = calculateRunningAverage(data, ['value'], 2)

            expect(result[0]).toMatchObject({ week: '2024-01-01', label: 'First' })
            expect(result[1]).toMatchObject({ week: '2024-01-08', label: 'Second' })
        })
    })

    describe('rounding behavior', () => {
        it('should round to 2 decimal places', () => {
            const data = [
                { value: 10 },
                { value: 15 },
                { value: 18 },
            ]

            const result = calculateRunningAverage(data, ['value'], 3)

            // avg(10, 15, 18) = 14.333... should round to 14.33
            expect(result[2].value_avg).toBe(14.33)
        })

        it('should handle integer averages without unnecessary decimals', () => {
            const data = [
                { value: 10 },
                { value: 20 },
            ]

            const result = calculateRunningAverage(data, ['value'], 2)

            // avg(10, 20) = 15.00 but should display as 15
            expect(result[1].value_avg).toBe(15)
        })
    })

    describe('real-world scenario: weekly ticket trends', () => {
        it('should smooth out weekly spikes correctly', () => {
            const weeklyData = [
                { week: '2024-W1', opened: 10, closed: 8 },
                { week: '2024-W2', opened: 50, closed: 10 }, // spike!
                { week: '2024-W3', opened: 12, closed: 15 },
                { week: '2024-W4', opened: 15, closed: 12 },
                { week: '2024-W5', opened: 14, closed: 13 },
            ]

            const result = calculateRunningAverage(weeklyData, ['opened', 'closed'], 4)

            // Week 5: 4-week average should smooth out the week 2 spike
            // opened avg = (50 + 12 + 15 + 14) / 4 = 22.75
            expect(result[4].opened_avg).toBe(22.75)
            
            // Verify spike is smoothed compared to raw value
            expect(result[4].opened_avg).toBeLessThan(result[1].opened) // 22.75 < 50
            expect(result[4].opened_avg).toBeGreaterThan(result[4].opened) // 22.75 > 14
        })
    })
})

