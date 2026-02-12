// src/components/dashboards/__tests__/ResolutionSLASection.test.tsx
import { render, screen } from '@testing-library/react'
import { ResolutionSLASection } from '../ResolutionSLASection'

// Mock child components
jest.mock('../ResolutionPercentileChart', () => ({
    ResolutionPercentileChart: ({ p50, p75, p90 }: any) => (
        <div data-testid="resolution-percentile-chart">
            <div>P50: {p50}</div>
            <div>P75: {p75}</div>
            <div>P90: {p90}</div>
        </div>
    )
}))

jest.mock('../TimeBucketChart', () => ({
    TimeBucketChart: ({ title, data, isLoading }: any) => (
        <div data-testid="time-bucket-chart">
            <h3>{title}</h3>
            {isLoading ? <div>Loading...</div> : <div>Buckets: {data?.length || 0}</div>}
        </div>
    )
}))

jest.mock('../TimeSeriesChart', () => ({
    TimeSeriesChart: ({ title, data, lines }: any) => (
        <div data-testid="time-series-chart">
            <h3>{title}</h3>
            <div>Data points: {data?.length || 0}</div>
            <div>Lines: {lines?.length || 0}</div>
        </div>
    )
}))

jest.mock('../RefreshButton', () => ({
    RefreshButton: ({ onRefresh, isRefreshing }: any) => (
        <button 
            data-testid="refresh-button" 
            onClick={onRefresh}
            disabled={isRefreshing}
        >
            {isRefreshing ? 'Refreshing...' : 'Refresh'}
        </button>
    )
}))

// Mock Recharts
jest.mock('recharts', () => ({
    ResponsiveContainer: ({ children }: any) => <div data-testid="responsive-container">{children}</div>,
    BarChart: ({ children }: any) => <div data-testid="bar-chart">{children}</div>,
    Bar: () => <div data-testid="bar" />,
    CartesianGrid: () => <div data-testid="grid" />,
    XAxis: () => <div data-testid="x-axis" />,
    YAxis: () => <div data-testid="y-axis" />,
    Tooltip: () => <div data-testid="tooltip" />,
}))

describe('ResolutionSLASection', () => {
    // Mock values
    const mockResolutionPercentiles = { p50: 16200, p75: 44280, p90: 89280 } // seconds
    const mockResolutionDurationDistribution = [
        { label: '< 15 min', count: 2, minMinutes: 0, maxMinutes: 15 },
        { label: '15-30 min', count: 1, minMinutes: 15, maxMinutes: 30 },
        { label: '30-60 min', count: 1, minMinutes: 30, maxMinutes: 60 },
        { label: '1-2 hours', count: 1, minMinutes: 60, maxMinutes: 120 },
    ]
    const mockResolutionTimesByWeek = [
        { week: '2024-W01', p50: 16200, p75: 44280, p90: 89280 }, // 4.5h, 12.3h, 24.8h in seconds
        { week: '2024-W02', p50: 18720, p75: 47160, p90: 94680 }, // 5.2h, 13.1h, 26.3h in seconds
    ]
    const mockResolutionTimeByTagInHours = [
        { tag: 'bug', p50: 5.5, p90: 15.2 },
        { tag: 'feature', p50: 8.3, p90: 22.1 },
    ]
    const mockUnresolvedTicketAges = { p50: '12h 30m', p90: '2 days 5h 15m' }
    const mockIncomingVsResolvedRate = [
        { time: '2024-01-01 10:00', incoming: 10, resolved: 8 },
        { time: '2024-01-01 11:00', incoming: 15, resolved: 12 },
    ]
    const mockOnRefresh = jest.fn()

    beforeEach(() => {
        jest.clearAllMocks()
    })

    describe('Rendering', () => {
        it('should render section header', () => {
            render(
                <ResolutionSLASection
                    resolutionPercentiles={mockResolutionPercentiles}
                    resolutionDurationDistribution={mockResolutionDurationDistribution}
                    resolutionTimesByWeek={mockResolutionTimesByWeek}
                    resolutionTimeByTagInHours={mockResolutionTimeByTagInHours}
                    unresolvedTicketAges={mockUnresolvedTicketAges}
                    incomingVsResolvedRate={mockIncomingVsResolvedRate}
                    isResolutionDistributionLoading={false}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            expect(screen.getByText('Resolution Performance')).toBeInTheDocument()
            expect(screen.getByText('Monitor ticket resolution times and trends')).toBeInTheDocument()
        })

        it('should render refresh button', () => {
            render(
                <ResolutionSLASection
                    resolutionPercentiles={mockResolutionPercentiles}
                    resolutionDurationDistribution={mockResolutionDurationDistribution}
                    resolutionTimesByWeek={mockResolutionTimesByWeek}
                    resolutionTimeByTagInHours={mockResolutionTimeByTagInHours}
                    unresolvedTicketAges={mockUnresolvedTicketAges}
                    incomingVsResolvedRate={mockIncomingVsResolvedRate}
                    isResolutionDistributionLoading={false}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            expect(screen.getByTestId('refresh-button')).toBeInTheDocument()
        })

        it('should render ResolutionPercentileChart when data exists', () => {
            render(
                <ResolutionSLASection
                    resolutionPercentiles={mockResolutionPercentiles}
                    resolutionDurationDistribution={mockResolutionDurationDistribution}
                    resolutionTimesByWeek={mockResolutionTimesByWeek}
                    resolutionTimeByTagInHours={mockResolutionTimeByTagInHours}
                    unresolvedTicketAges={mockUnresolvedTicketAges}
                    incomingVsResolvedRate={mockIncomingVsResolvedRate}
                    isResolutionDistributionLoading={false}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            expect(screen.getByTestId('resolution-percentile-chart')).toBeInTheDocument()
        })

        it('should render TimeBucketChart for distribution', () => {
            render(
                <ResolutionSLASection
                    resolutionPercentiles={mockResolutionPercentiles}
                    resolutionDurationDistribution={mockResolutionDurationDistribution}
                    resolutionTimesByWeek={mockResolutionTimesByWeek}
                    resolutionTimeByTagInHours={mockResolutionTimeByTagInHours}
                    unresolvedTicketAges={mockUnresolvedTicketAges}
                    incomingVsResolvedRate={mockIncomingVsResolvedRate}
                    isResolutionDistributionLoading={false}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            expect(screen.getByText('Ticket Resolution Duration Distribution')).toBeInTheDocument()
        })

        it('should render TimeSeriesChart for weekly trends', () => {
            render(
                <ResolutionSLASection
                    resolutionPercentiles={mockResolutionPercentiles}
                    resolutionDurationDistribution={mockResolutionDurationDistribution}
                    resolutionTimesByWeek={mockResolutionTimesByWeek}
                    resolutionTimeByTagInHours={mockResolutionTimeByTagInHours}
                    unresolvedTicketAges={mockUnresolvedTicketAges}
                    incomingVsResolvedRate={mockIncomingVsResolvedRate}
                    isResolutionDistributionLoading={false}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            expect(screen.getByText('Resolution Times by Week (P50/P75/P90)')).toBeInTheDocument()
        })

        it('should render Resolution Time by Tag chart', () => {
            render(
                <ResolutionSLASection
                    resolutionPercentiles={mockResolutionPercentiles}
                    resolutionDurationDistribution={mockResolutionDurationDistribution}
                    resolutionTimesByWeek={mockResolutionTimesByWeek}
                    resolutionTimeByTagInHours={mockResolutionTimeByTagInHours}
                    unresolvedTicketAges={mockUnresolvedTicketAges}
                    incomingVsResolvedRate={mockIncomingVsResolvedRate}
                    isResolutionDistributionLoading={false}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            expect(screen.getByText('Resolution Time by Tag (P50/P90) - Hours')).toBeInTheDocument()
        })

        it('should render Unresolved Ticket Ages when data exists', () => {
            render(
                <ResolutionSLASection
                    resolutionPercentiles={mockResolutionPercentiles}
                    resolutionDurationDistribution={mockResolutionDurationDistribution}
                    resolutionTimesByWeek={mockResolutionTimesByWeek}
                    resolutionTimeByTagInHours={mockResolutionTimeByTagInHours}
                    unresolvedTicketAges={mockUnresolvedTicketAges}
                    incomingVsResolvedRate={mockIncomingVsResolvedRate}
                    isResolutionDistributionLoading={false}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            // Check title is present, and the P50/P90 labels
            expect(screen.getAllByText('Unresolved Ticket Ages').length).toBeGreaterThanOrEqual(1)
            expect(screen.getByText('P50 (Median)')).toBeInTheDocument()
            expect(screen.getByText('P90')).toBeInTheDocument()
        })

        it('should render Incoming vs Resolved Rate chart', () => {
            render(
                <ResolutionSLASection
                    resolutionPercentiles={mockResolutionPercentiles}
                    resolutionDurationDistribution={mockResolutionDurationDistribution}
                    resolutionTimesByWeek={mockResolutionTimesByWeek}
                    resolutionTimeByTagInHours={mockResolutionTimeByTagInHours}
                    unresolvedTicketAges={mockUnresolvedTicketAges}
                    incomingVsResolvedRate={mockIncomingVsResolvedRate}
                    isResolutionDistributionLoading={false}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            expect(screen.getByText('Incoming vs Resolved Rate - Performance SLA')).toBeInTheDocument()
        })
    })

    describe('Empty States', () => {
        it('should show "No data available" when percentiles are undefined', () => {
            render(
                <ResolutionSLASection
                    resolutionPercentiles={undefined}
                    resolutionDurationDistribution={mockResolutionDurationDistribution}
                    resolutionTimesByWeek={mockResolutionTimesByWeek}
                    resolutionTimeByTagInHours={mockResolutionTimeByTagInHours}
                    unresolvedTicketAges={mockUnresolvedTicketAges}
                    incomingVsResolvedRate={mockIncomingVsResolvedRate}
                    isResolutionDistributionLoading={false}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            expect(screen.getByText('No data available')).toBeInTheDocument()
        })

        it('should handle empty resolution times by week', () => {
            render(
                <ResolutionSLASection
                    resolutionPercentiles={mockResolutionPercentiles}
                    resolutionDurationDistribution={mockResolutionDurationDistribution}
                    resolutionTimesByWeek={[]}
                    resolutionTimeByTagInHours={mockResolutionTimeByTagInHours}
                    unresolvedTicketAges={mockUnresolvedTicketAges}
                    incomingVsResolvedRate={mockIncomingVsResolvedRate}
                    isResolutionDistributionLoading={false}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            expect(screen.getByText('Data points: 0')).toBeInTheDocument()
        })

        it('should show "No data available" when resolution time by tag is empty', () => {
            render(
                <ResolutionSLASection
                    resolutionPercentiles={mockResolutionPercentiles}
                    resolutionDurationDistribution={mockResolutionDurationDistribution}
                    resolutionTimesByWeek={mockResolutionTimesByWeek}
                    resolutionTimeByTagInHours={[]}
                    unresolvedTicketAges={mockUnresolvedTicketAges}
                    incomingVsResolvedRate={mockIncomingVsResolvedRate}
                    isResolutionDistributionLoading={false}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            // Should show "No data available" for the tag chart
            const noDataTexts = screen.getAllByText(/No data available/i)
            expect(noDataTexts.length).toBeGreaterThanOrEqual(1)
        })

        it('should show "No data available" for Unresolved Ticket Ages when undefined', () => {
            render(
                <ResolutionSLASection
                    resolutionPercentiles={mockResolutionPercentiles}
                    resolutionDurationDistribution={mockResolutionDurationDistribution}
                    resolutionTimesByWeek={mockResolutionTimesByWeek}
                    resolutionTimeByTagInHours={mockResolutionTimeByTagInHours}
                    unresolvedTicketAges={undefined}
                    incomingVsResolvedRate={mockIncomingVsResolvedRate}
                    isResolutionDistributionLoading={false}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            // Unresolved Ticket Ages section should still appear but show no data
            expect(screen.getByText('Unresolved Ticket Ages')).toBeInTheDocument()
            const noDataTexts = screen.getAllByText(/No data available/i)
            expect(noDataTexts.length).toBeGreaterThanOrEqual(1)
        })

        it('should handle empty incoming vs resolved rate', () => {
            render(
                <ResolutionSLASection
                    resolutionPercentiles={mockResolutionPercentiles}
                    resolutionDurationDistribution={mockResolutionDurationDistribution}
                    resolutionTimesByWeek={mockResolutionTimesByWeek}
                    resolutionTimeByTagInHours={mockResolutionTimeByTagInHours}
                    unresolvedTicketAges={mockUnresolvedTicketAges}
                    incomingVsResolvedRate={[]}
                    isResolutionDistributionLoading={false}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            // Chart title should still be there but with no data
            expect(screen.getByText('Incoming vs Resolved Rate - Performance SLA')).toBeInTheDocument()
        })
    })

    describe('Loading States', () => {
        it('should show loading state for distribution chart', () => {
            render(
                <ResolutionSLASection
                    resolutionPercentiles={mockResolutionPercentiles}
                    resolutionDurationDistribution={mockResolutionDurationDistribution}
                    resolutionTimesByWeek={mockResolutionTimesByWeek}
                    resolutionTimeByTagInHours={mockResolutionTimeByTagInHours}
                    unresolvedTicketAges={mockUnresolvedTicketAges}
                    incomingVsResolvedRate={mockIncomingVsResolvedRate}
                    isResolutionDistributionLoading={true}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            const loadingTexts = screen.getAllByText('Loading...')
            expect(loadingTexts.length).toBeGreaterThanOrEqual(1)
        })

        it('should show refreshing state', () => {
            render(
                <ResolutionSLASection
                    resolutionPercentiles={mockResolutionPercentiles}
                    resolutionDurationDistribution={mockResolutionDurationDistribution}
                    resolutionTimesByWeek={mockResolutionTimesByWeek}
                    resolutionTimeByTagInHours={mockResolutionTimeByTagInHours}
                    unresolvedTicketAges={mockUnresolvedTicketAges}
                    incomingVsResolvedRate={mockIncomingVsResolvedRate}
                    isResolutionDistributionLoading={false}
                    isRefreshing={true}
                    onRefresh={mockOnRefresh}
                />
            )

            expect(screen.getByText('Refreshing...')).toBeInTheDocument()
        })
    })

    describe('Refresh Functionality', () => {
        it('should call onRefresh when refresh button is clicked', () => {
            render(
                <ResolutionSLASection
                    resolutionPercentiles={mockResolutionPercentiles}
                    resolutionDurationDistribution={mockResolutionDurationDistribution}
                    resolutionTimesByWeek={mockResolutionTimesByWeek}
                    resolutionTimeByTagInHours={mockResolutionTimeByTagInHours}
                    unresolvedTicketAges={mockUnresolvedTicketAges}
                    incomingVsResolvedRate={mockIncomingVsResolvedRate}
                    isResolutionDistributionLoading={false}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            const refreshButton = screen.getByTestId('refresh-button')
            refreshButton.click()

            expect(mockOnRefresh).toHaveBeenCalledTimes(1)
        })
    })

    describe('Data Integrity', () => {
        it('should render all charts with valid data', () => {
            render(
                <ResolutionSLASection
                    resolutionPercentiles={mockResolutionPercentiles}
                    resolutionDurationDistribution={mockResolutionDurationDistribution}
                    resolutionTimesByWeek={mockResolutionTimesByWeek}
                    resolutionTimeByTagInHours={mockResolutionTimeByTagInHours}
                    unresolvedTicketAges={mockUnresolvedTicketAges}
                    incomingVsResolvedRate={mockIncomingVsResolvedRate}
                    isResolutionDistributionLoading={false}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            // All major charts/sections should be present
            expect(screen.getByTestId('resolution-percentile-chart')).toBeInTheDocument()
            expect(screen.getByTestId('time-bucket-chart')).toBeInTheDocument()
            expect(screen.getAllByTestId('time-series-chart')).toHaveLength(2) // Weekly trends + incoming/resolved
            expect(screen.getByTestId('bar-chart')).toBeInTheDocument() // Resolution by tag
        })

        it('should handle large datasets', () => {
            const largeWeeklyData = Array.from({ length: 52 }, (_, i) => ({
                week: `2024-W${i + 1}`,
                p50: Math.random() * 10,
                p75: Math.random() * 20,
                p90: Math.random() * 30,
            }))

            const largeTagData = Array.from({ length: 15 }, (_, i) => ({
                tag: `tag-${i}`,
                p50: Math.random() * 10,
                p90: Math.random() * 20,
            }))

            render(
                <ResolutionSLASection
                    resolutionPercentiles={mockResolutionPercentiles}
                    resolutionDurationDistribution={mockResolutionDurationDistribution}
                    resolutionTimesByWeek={largeWeeklyData}
                    resolutionTimeByTagInHours={largeTagData}
                    unresolvedTicketAges={mockUnresolvedTicketAges}
                    incomingVsResolvedRate={mockIncomingVsResolvedRate}
                    isResolutionDistributionLoading={false}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            // Should handle 52 weeks of data
            expect(screen.getByText('Data points: 52')).toBeInTheDocument()
        })
    })

    describe('Layout', () => {
        it('should render charts in responsive grid', () => {
            const { container } = render(
                <ResolutionSLASection
                    resolutionPercentiles={mockResolutionPercentiles}
                    resolutionDurationDistribution={mockResolutionDurationDistribution}
                    resolutionTimesByWeek={mockResolutionTimesByWeek}
                    resolutionTimeByTagInHours={mockResolutionTimeByTagInHours}
                    unresolvedTicketAges={mockUnresolvedTicketAges}
                    incomingVsResolvedRate={mockIncomingVsResolvedRate}
                    isResolutionDistributionLoading={false}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            // Check for grid containers
            const grids = container.querySelectorAll('.grid')
            expect(grids.length).toBeGreaterThanOrEqual(2)
        })
    })
})

