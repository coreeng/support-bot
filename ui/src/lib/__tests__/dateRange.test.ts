import { getDateRangeFromFilter, PRESET_DAYS, DateFilter } from '../dateRange'

// Fix "now" so date arithmetic produces deterministic, human-verifiable results.
// 2024-01-15 is chosen because it yields clean boundaries:
//   lastWeek    → 2024-01-08
//   last2Weeks  → 2024-01-01
//   lastMonth   → 2023-12-16  (30 days back across a month boundary)
//   lastYear    → 2023-01-15  (365 days back; no Feb-29 crossing from January)
const FIXED_NOW = new Date('2024-01-15T00:00:00.000Z')
const TODAY = '2024-01-15'

beforeEach(() => {
    jest.useFakeTimers()
    jest.setSystemTime(FIXED_NOW)
})

afterEach(() => {
    jest.useRealTimers()
})

// Shared base options used by most tests.
// fallbackValue is typed as the full Exclude<DateFilter, 'custom' | 'all'> union so
// that TypeScript infers T = DateFilter for every spread call, keeping fallbackValue
// valid regardless of which dateFilter literal is used in individual tests.
const BASE = {
    customValue: 'custom' as const,
    fallbackValue: 'lastWeek' as Exclude<DateFilter, 'custom' | 'all'>,
    allValue: 'all' as const,
    customDateRange: {},
    presetDays: PRESET_DAYS,
}

describe('getDateRangeFromFilter', () => {
    describe('named presets', () => {
        it('lastWeek returns the last 7 days', () => {
            expect(getDateRangeFromFilter({ ...BASE, dateFilter: 'lastWeek' as DateFilter }))
                .toEqual({ from: '2024-01-08', to: TODAY })
        })

        it('last2Weeks returns the last 14 days', () => {
            expect(getDateRangeFromFilter({ ...BASE, dateFilter: 'last2Weeks' as DateFilter }))
                .toEqual({ from: '2024-01-01', to: TODAY })
        })

        it('lastMonth returns the last 30 days', () => {
            expect(getDateRangeFromFilter({ ...BASE, dateFilter: 'lastMonth' as DateFilter }))
                .toEqual({ from: '2023-12-16', to: TODAY })
        })

        it('lastYear returns the last 365 days', () => {
            expect(getDateRangeFromFilter({ ...BASE, dateFilter: 'lastYear' as DateFilter }))
                .toEqual({ from: '2023-01-15', to: TODAY })
        })
    })

    describe('custom date range', () => {
        it('returns the exact custom dates when both are provided', () => {
            expect(getDateRangeFromFilter({
                ...BASE,
                dateFilter: 'custom' as DateFilter,
                customDateRange: { start: '2024-01-01', end: '2024-01-10' },
            })).toEqual({ from: '2024-01-01', to: '2024-01-10' })
        })

        it('falls back to fallbackValue when start date is missing', () => {
            expect(getDateRangeFromFilter({
                ...BASE,
                dateFilter: 'custom' as DateFilter,
                customDateRange: { end: '2024-01-10' },
            })).toEqual({ from: '2024-01-08', to: TODAY })
        })

        it('falls back to fallbackValue when end date is missing', () => {
            expect(getDateRangeFromFilter({
                ...BASE,
                dateFilter: 'custom' as DateFilter,
                customDateRange: { start: '2024-01-01' },
            })).toEqual({ from: '2024-01-08', to: TODAY })
        })

        it('falls back to fallbackValue when both dates are missing', () => {
            expect(getDateRangeFromFilter({
                ...BASE,
                dateFilter: 'custom' as DateFilter,
                customDateRange: {},
            })).toEqual({ from: '2024-01-08', to: TODAY })
        })
    })

    describe('allValue', () => {
        it('returns { from: undefined, to: undefined } when dateFilter matches allValue', () => {
            expect(getDateRangeFromFilter({ ...BASE, dateFilter: 'all' as DateFilter }))
                .toEqual({ from: undefined, to: undefined })
        })

        it('does not treat "all" as allValue when allValue is not provided', () => {
            const { allValue: _, ...withoutAllValue } = BASE
            // 'all' is not in presetDays, so it falls through to the unknown-filter branch.
            expect(getDateRangeFromFilter({ ...withoutAllValue, dateFilter: 'all' as DateFilter }))
                .toEqual({ from: undefined, to: undefined })
        })
    })

    describe('unknown filter', () => {
        it('returns { from: undefined, to: undefined } for a filter not in presetDays', () => {
            expect(getDateRangeFromFilter({ ...BASE, dateFilter: 'bogus' as unknown as DateFilter }))
                .toEqual({ from: undefined, to: undefined })
        })

        it('returns { from: undefined, to: undefined } for an empty string filter', () => {
            expect(getDateRangeFromFilter({ ...BASE, dateFilter: '' as unknown as DateFilter }))
                .toEqual({ from: undefined, to: undefined })
        })
    })

    describe('custom presetDays subset', () => {
        it('uses only the provided subset of preset days', () => {
            // Pages that expose fewer options (e.g. no lastYear) should still work.
            expect(getDateRangeFromFilter({
                ...BASE,
                presetDays: { lastWeek: 7, lastMonth: 30 },
                dateFilter: 'lastMonth' as DateFilter,
            })).toEqual({ from: '2023-12-16', to: TODAY })
        })

        it('returns undefined range for a preset omitted from the subset', () => {
            expect(getDateRangeFromFilter({
                ...BASE,
                presetDays: { lastWeek: 7 },
                dateFilter: 'lastYear' as DateFilter,
            })).toEqual({ from: undefined, to: undefined })
        })
    })
})

