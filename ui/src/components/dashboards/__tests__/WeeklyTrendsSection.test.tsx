// src/components/dashboards/__tests__/WeeklyTrendsSection.test.tsx
import { render, screen } from '@testing-library/react'
import { WeeklyTrendsSection } from '../WeeklyTrendsSection'

// Mock Recharts to avoid rendering issues in tests
jest.mock('recharts', () => ({
    ResponsiveContainer: ({ children }: any) => <div data-testid="responsive-container">{children}</div>,
    LineChart: ({ children }: any) => <div data-testid="line-chart">{children}</div>,
    Line: () => <div data-testid="line" />,
    CartesianGrid: () => <div data-testid="grid" />,
    XAxis: () => <div data-testid="x-axis" />,
    YAxis: () => <div data-testid="y-axis" />,
    Tooltip: () => <div data-testid="tooltip" />,
    Legend: () => <div data-testid="legend" />,
}))

// Mock HorizontalBarChart
jest.mock('../HorizontalBarChart', () => ({
    HorizontalBarChart: () => <div data-testid="horizontal-bar-chart">Top Tags Chart</div>
}))

// Mock RefreshButton
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

describe('WeeklyTrendsSection', () => {
    const mockWeeklyComparison = [
        { label: 'opened', thisWeek: 10, lastWeek: 8, change: 2 },
        { label: 'closed', thisWeek: 12, lastWeek: 10, change: 2 },
        { label: 'escalated', thisWeek: 3, lastWeek: 2, change: 1 },
        { label: 'stale', thisWeek: 1, lastWeek: 1, change: 0 },
    ]

    const mockWeeklyTicketCounts = [
        { week: '2024-01-01', opened: 10, closed: 8, escalated: 2, stale: 1 },
        { week: '2024-01-08', opened: 15, closed: 12, escalated: 3, stale: 2 },
        { week: '2024-01-15', opened: 20, closed: 18, escalated: 4, stale: 1 },
        { week: '2024-01-22', opened: 12, closed: 10, escalated: 2, stale: 0 },
    ]

    const mockTopEscalatedTags = [
        { tag: 'bug', count: 10 },
        { tag: 'feature', count: 5 },
    ]

    const mockOnRefresh = jest.fn()

    beforeEach(() => {
        jest.clearAllMocks()
    })

    describe('Rendering', () => {
        it('should render section header', () => {
            render(
                <WeeklyTrendsSection
                    weeklyComparison={mockWeeklyComparison}
                    weeklyTicketCounts={mockWeeklyTicketCounts}
                    topEscalatedTags={mockTopEscalatedTags}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            expect(screen.getByText('Weekly Trends')).toBeInTheDocument()
            expect(screen.getByText('Monitor weekly metrics and patterns')).toBeInTheDocument()
        })

        it('should render refresh button', () => {
            render(
                <WeeklyTrendsSection
                    weeklyComparison={mockWeeklyComparison}
                    weeklyTicketCounts={mockWeeklyTicketCounts}
                    topEscalatedTags={mockTopEscalatedTags}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            expect(screen.getByTestId('refresh-button')).toBeInTheDocument()
        })

        it('should render weekly comparison cards', () => {
            render(
                <WeeklyTrendsSection
                    weeklyComparison={mockWeeklyComparison}
                    weeklyTicketCounts={mockWeeklyTicketCounts}
                    topEscalatedTags={mockTopEscalatedTags}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            // Cards show capitalized label + " This Week"
            expect(screen.getByText('Opened This Week', { exact: false })).toBeInTheDocument()
            expect(screen.getByText('Closed This Week', { exact: false })).toBeInTheDocument()
            expect(screen.getByText('Escalated This Week', { exact: false })).toBeInTheDocument()
            expect(screen.getByText('Stale This Week', { exact: false })).toBeInTheDocument()
        })

        it('should render both chart titles', () => {
            render(
                <WeeklyTrendsSection
                    weeklyComparison={mockWeeklyComparison}
                    weeklyTicketCounts={mockWeeklyTicketCounts}
                    topEscalatedTags={mockTopEscalatedTags}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            expect(screen.getByText('Tickets Per Week Breakdown')).toBeInTheDocument()
            expect(screen.getByText('Running Average Trends')).toBeInTheDocument()
        })

        it('should render top tags chart', () => {
            render(
                <WeeklyTrendsSection
                    weeklyComparison={mockWeeklyComparison}
                    weeklyTicketCounts={mockWeeklyTicketCounts}
                    topEscalatedTags={mockTopEscalatedTags}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            expect(screen.getByTestId('horizontal-bar-chart')).toBeInTheDocument()
        })
    })

    describe('Running Average Calculation', () => {
        it('should calculate running averages for all metrics', () => {
            render(
                <WeeklyTrendsSection
                    weeklyComparison={mockWeeklyComparison}
                    weeklyTicketCounts={mockWeeklyTicketCounts}
                    topEscalatedTags={mockTopEscalatedTags}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            // Should have LineChart for both original and running average
            const lineCharts = screen.getAllByTestId('line-chart')
            expect(lineCharts).toHaveLength(2) // Original + Running Average
        })

        it('should show "Running Average Trends" chart when data exists', () => {
            render(
                <WeeklyTrendsSection
                    weeklyComparison={mockWeeklyComparison}
                    weeklyTicketCounts={mockWeeklyTicketCounts}
                    topEscalatedTags={mockTopEscalatedTags}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            expect(screen.getByText('Running Average Trends')).toBeInTheDocument()
        })
    })

    describe('Empty States', () => {
        it('should show "No data available" when weekly counts are empty', () => {
            render(
                <WeeklyTrendsSection
                    weeklyComparison={mockWeeklyComparison}
                    weeklyTicketCounts={[]}
                    topEscalatedTags={mockTopEscalatedTags}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            const noDataTexts = screen.getAllByText('No data available')
            expect(noDataTexts.length).toBeGreaterThan(0)
        })

        it('should show "No data available" when weekly counts are undefined', () => {
            render(
                <WeeklyTrendsSection
                    weeklyComparison={mockWeeklyComparison}
                    weeklyTicketCounts={undefined}
                    topEscalatedTags={mockTopEscalatedTags}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            const noDataTexts = screen.getAllByText('No data available')
            expect(noDataTexts.length).toBeGreaterThan(0)
        })

        it('should handle empty weekly comparison gracefully', () => {
            render(
                <WeeklyTrendsSection
                    weeklyComparison={[]}
                    weeklyTicketCounts={mockWeeklyTicketCounts}
                    topEscalatedTags={mockTopEscalatedTags}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            // Should not crash, section should still render
            expect(screen.getByText('Weekly Trends')).toBeInTheDocument()
        })
    })

    describe('Percentage Calculations', () => {
        it('should show positive percentage change correctly', () => {
            render(
                <WeeklyTrendsSection
                    weeklyComparison={mockWeeklyComparison}
                    weeklyTicketCounts={mockWeeklyTicketCounts}
                    topEscalatedTags={mockTopEscalatedTags}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            // Opened: thisWeek=10, lastWeek=8, change=2
            // Percentage: (2/8)*100 = 25%
            expect(screen.getByText(/\+25%/i)).toBeInTheDocument()
        })

        it('should handle zero last week correctly', () => {
            const comparisonWithZero = [
                { label: 'opened', thisWeek: 5, lastWeek: 0, change: 5 },
            ]

            render(
                <WeeklyTrendsSection
                    weeklyComparison={comparisonWithZero}
                    weeklyTicketCounts={mockWeeklyTicketCounts}
                    topEscalatedTags={mockTopEscalatedTags}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            // Should show +500% (5 * 100%)
            expect(screen.getByText(/\+500%/i)).toBeInTheDocument()
        })

        it('should handle zero change correctly', () => {
            const comparisonWithNoChange = [
                { label: 'stale', thisWeek: 1, lastWeek: 1, change: 0 },
            ]

            render(
                <WeeklyTrendsSection
                    weeklyComparison={comparisonWithNoChange}
                    weeklyTicketCounts={mockWeeklyTicketCounts}
                    topEscalatedTags={mockTopEscalatedTags}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            // Should show 0% when change / lastWeek = 0 / 1 = 0%
            expect(screen.getByText(/(0% vs last week)/i)).toBeInTheDocument()
        })
    })

    describe('Refresh Functionality', () => {
        it('should call onRefresh when refresh button is clicked', () => {
            render(
                <WeeklyTrendsSection
                    weeklyComparison={mockWeeklyComparison}
                    weeklyTicketCounts={mockWeeklyTicketCounts}
                    topEscalatedTags={mockTopEscalatedTags}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            const refreshButton = screen.getByTestId('refresh-button')
            refreshButton.click()

            expect(mockOnRefresh).toHaveBeenCalledTimes(1)
        })

        it('should show refreshing state', () => {
            render(
                <WeeklyTrendsSection
                    weeklyComparison={mockWeeklyComparison}
                    weeklyTicketCounts={mockWeeklyTicketCounts}
                    topEscalatedTags={mockTopEscalatedTags}
                    isRefreshing={true}
                    onRefresh={mockOnRefresh}
                />
            )

            expect(screen.getByText('Refreshing...')).toBeInTheDocument()
        })
    })

    describe('Data Integrity', () => {
        it('should handle single week of data', () => {
            const singleWeek = [
                { week: '2024-01-01', opened: 10, closed: 8, escalated: 2, stale: 1 },
            ]

            render(
                <WeeklyTrendsSection
                    weeklyComparison={mockWeeklyComparison}
                    weeklyTicketCounts={singleWeek}
                    topEscalatedTags={mockTopEscalatedTags}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            // Should not crash
            expect(screen.getByText('Tickets Per Week Breakdown')).toBeInTheDocument()
        })

        it('should handle large dataset', () => {
            const largeDataset = Array.from({ length: 52 }, (_, i) => ({
                week: `2024-W${i + 1}`,
                opened: Math.floor(Math.random() * 50),
                closed: Math.floor(Math.random() * 50),
                escalated: Math.floor(Math.random() * 10),
                stale: Math.floor(Math.random() * 5),
            }))

            render(
                <WeeklyTrendsSection
                    weeklyComparison={mockWeeklyComparison}
                    weeklyTicketCounts={largeDataset}
                    topEscalatedTags={mockTopEscalatedTags}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            // Should handle a year's worth of data
            expect(screen.getByText('Running Average Trends')).toBeInTheDocument()
        })
    })
})

