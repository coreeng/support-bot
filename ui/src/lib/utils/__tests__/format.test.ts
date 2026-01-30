// src/lib/utils/__tests__/format.test.ts
import { 
    formatInterval, 
    calculatePercentageChange,
    formatDate
} from '../format'
import { formatHoursToDHMS } from '../chart'

describe('formatInterval', () => {
    it('should format simple time intervals', () => {
        expect(formatInterval('20:21:31')).toBe('20h 21m 31s')
        expect(formatInterval('1:05:30')).toBe('1h 5m 30s')
        expect(formatInterval('0:30:45')).toBe('30m 45s')
    })

    it('should format intervals with days', () => {
        // Note: formatInterval skips zero components
        expect(formatInterval('1 day 02:30:00')).toBe('1 day 2h 30m')
        expect(formatInterval('2 days 10:15:30')).toBe('2 days 10h 15m 30s')
        expect(formatInterval('5 days 00:00:01')).toBe('5 days 1s')
    })

    it('should remove microseconds', () => {
        expect(formatInterval('20:21:31.44065')).toBe('20h 21m 31s')
        expect(formatInterval('19 days 07:29:05.34065')).toBe('19 days 7h 29m 5s')
    })

    it('should handle zero values', () => {
        expect(formatInterval('0:00:00')).toBe('0s')
        expect(formatInterval('0:00:05')).toBe('5s')
    })

    it('should skip zero components', () => {
        expect(formatInterval('0:05:00')).toBe('5m')
        expect(formatInterval('2:00:00')).toBe('2h')
        expect(formatInterval('1 day 00:00:00')).toBe('1 day')
    })

    it('should handle empty or invalid input', () => {
        expect(formatInterval('')).toBe('')
    })
})

describe('formatHoursToDHMS (from chart utils)', () => {
    it('should format hours to days-hours-minutes-seconds', () => {
        // Note: This is from chart.ts and has format "Xd Yh Zm Ws"
        const result1 = formatHoursToDHMS(1)
        expect(result1).toContain('h')
        expect(result1).toContain('m')
        expect(result1).toContain('s')
    })

    it('should handle fractional hours', () => {
        const result = formatHoursToDHMS(1.5)
        expect(result).toMatch(/\d+d \d+h \d+m \d+s/)
    })

    it('should handle zero or invalid', () => {
        expect(formatHoursToDHMS(0)).toBe('0:00:00')
        expect(formatHoursToDHMS(NaN)).toBe('0:00:00')
    })
})

describe('calculatePercentageChange', () => {
    it('should calculate positive percentage change', () => {
        expect(calculatePercentageChange(150, 100)).toBe('+50%')
        expect(calculatePercentageChange(200, 100)).toBe('+100%')
        expect(calculatePercentageChange(110, 100)).toBe('+10%')
    })

    it('should calculate negative percentage change', () => {
        expect(calculatePercentageChange(50, 100)).toBe('-50%')
        expect(calculatePercentageChange(75, 100)).toBe('-25%')
        expect(calculatePercentageChange(1, 100)).toBe('-99%')
    })

    it('should handle zero change', () => {
        expect(calculatePercentageChange(100, 100)).toBe('0%')
        expect(calculatePercentageChange(0, 0)).toBe('0%')
    })

    it('should handle going from 0 to N', () => {
        expect(calculatePercentageChange(1, 0)).toBe('+100%')
        expect(calculatePercentageChange(5, 0)).toBe('+500%')
        expect(calculatePercentageChange(10, 0)).toBe('+1000%')
    })

    it('should round to nearest integer', () => {
        expect(calculatePercentageChange(155, 100)).toBe('+55%')
        expect(calculatePercentageChange(154, 100)).toBe('+54%')
    })
})

describe('formatDate', () => {
    it('should format ISO date strings', () => {
        expect(formatDate('2025-01-15')).toBe('Jan 15, 2025')
        expect(formatDate('2025-12-31')).toBe('Dec 31, 2025')
    })

    it('should handle full ISO timestamps', () => {
        const formatted = formatDate('2025-11-05T14:20:00Z')
        expect(formatted).toMatch(/Nov 5, 2025/)
    })
})

