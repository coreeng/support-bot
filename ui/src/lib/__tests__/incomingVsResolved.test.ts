import {
    formatIncomingVsResolvedSeries,
    timeBucketResolutionForIncomingVsResolvedGranularity,
} from '../incomingVsResolved'

describe('timeBucketResolutionForIncomingVsResolvedGranularity', () => {
    it('maps backend granularity to chart resolution', () => {
        expect(timeBucketResolutionForIncomingVsResolvedGranularity('HOUR')).toBe('hour')
        expect(timeBucketResolutionForIncomingVsResolvedGranularity('DAY')).toBe('day')
        expect(timeBucketResolutionForIncomingVsResolvedGranularity('WEEK')).toBe('week')
    })

    it('warns and defaults to day for unknown backend granularity values', () => {
        const warn = jest.spyOn(console, 'warn').mockImplementation(() => undefined)

        expect(timeBucketResolutionForIncomingVsResolvedGranularity('MONTH' as never)).toBe('day')
        expect(warn).toHaveBeenCalledWith('Unknown IncomingVsResolvedGranularity: MONTH, defaulting to day')

        warn.mockRestore()
    })
})

describe('formatIncomingVsResolvedSeries', () => {
    it('formats incoming/resolved labels using backend granularity', () => {
        expect(formatIncomingVsResolvedSeries([
            { time: '2024-01-01T10:00:00Z', incoming: 1, resolved: 2 },
        ], 'HOUR')[0].time).toContain('10')
    })
})