export type CustomDateRange = { start?: string; end?: string }

type DateRangeOptions<T extends string> = {
    dateFilter: T
    customDateRange: CustomDateRange
    customValue: T
    fallbackValue: Exclude<T, 'custom' | 'all'>
    allValue?: T
    presetDays: Partial<Record<Exclude<T, 'custom' | 'all'>, number>>
}

const toDateString = (date: Date): string => date.toISOString().split('T')[0]

export function getDateRangeFromFilter<T extends string>({
    dateFilter,
    customDateRange,
    customValue,
    fallbackValue,
    allValue,
    presetDays,
}: DateRangeOptions<T>): { from?: string; to?: string } {
    if (allValue && dateFilter === allValue) {
        return { from: undefined, to: undefined }
    }

    if (dateFilter === customValue) {
        if (customDateRange.start && customDateRange.end) {
            return { from: customDateRange.start, to: customDateRange.end }
        }
        return getDateRangeFromFilter({
            dateFilter: fallbackValue as T,
            customDateRange,
            customValue,
            fallbackValue,
            allValue,
            presetDays,
        })
    }

    const days = presetDays[dateFilter as Exclude<T, 'custom' | 'all'>]
    if (days == null) {
        return { from: undefined, to: undefined }
    }

    const now = new Date()
    const to = toDateString(now)
    const fromDate = new Date(now)
    fromDate.setDate(now.getDate() - days)
    return { from: toDateString(fromDate), to }
}
