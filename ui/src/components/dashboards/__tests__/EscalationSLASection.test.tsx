// src/components/dashboards/__tests__/EscalationSLASection.test.tsx
/* eslint-disable @typescript-eslint/no-explicit-any */
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { EscalationSLASection } from '../EscalationSLASection'

// Mock child components
jest.mock('../HorizontalBarChart', () => ({
    HorizontalBarChart: ({ title, data }: any) => (
        <div data-testid="horizontal-bar-chart">
            <h3>{title}</h3>
            <div>Data items: {data?.length || 0}</div>
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
    BarChart: ({ children, data }: any) => (
        <div data-testid="bar-chart" data-items={data?.length}>
            {children}
        </div>
    ),
    Bar: () => <div data-testid="bar" />,
    CartesianGrid: () => <div data-testid="grid" />,
    XAxis: () => <div data-testid="x-axis" />,
    YAxis: () => <div data-testid="y-axis" />,
    Tooltip: () => <div data-testid="tooltip" />,
}))

describe('EscalationSLASection', () => {
    // Mock values in SECONDS (as returned by the database)
    const mockAvgEscalationDurationByTag = [
        { tag: 'bug', avgDuration: 30600 }, // 8.5 hours in seconds
        { tag: 'feature', avgDuration: 44280 }, // 12.3 hours in seconds
        { tag: 'incident', avgDuration: 15120 }, // 4.2 hours in seconds
    ]
    
    const mockEscalationPercentageByTag = [
        { tag: 'bug', count: 25 },
        { tag: 'feature', count: 18 },
        { tag: 'incident', count: 42 },
    ]
    
    const mockEscalationTrendsByDate = [
        { date: '2024-01-01', escalations: 10 },
        { date: '2024-01-02', escalations: 15 },
        { date: '2024-01-03', escalations: 12 },
    ]
    
    const mockEscalationsByTeam = [
        { assigneeName: 'Team A', totalEscalations: 30 },
        { assigneeName: 'Team B', totalEscalations: 25 },
        { assigneeName: 'Team C', totalEscalations: 20 },
    ]
    
    const mockEscalationsByImpact = [
        { impactLevel: 'productionBlocking', totalEscalations: 15 },
        { impactLevel: 'abnormalBehaviour', totalEscalations: 30 },
        { impactLevel: 'bauBlocking', totalEscalations: 10 },
    ]
    
    const mockOnRefresh = jest.fn()

    const renderSection = (ui: React.ReactElement) => {
        const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
        return render(
            <QueryClientProvider client={queryClient}>
                {ui}
            </QueryClientProvider>
        )
    }

    beforeEach(() => {
        jest.clearAllMocks()
    })

    describe('Rendering', () => {
        it('should render section header', () => {
            renderSection(
                <EscalationSLASection
                    avgEscalationDurationByTag={mockAvgEscalationDurationByTag}
                    escalationPercentageByTag={mockEscalationPercentageByTag}
                    escalationTrendsByDate={mockEscalationTrendsByDate}
                    escalationsByTeam={mockEscalationsByTeam}
                    escalationsByImpact={mockEscalationsByImpact}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            expect(screen.getByText('Escalation Analysis')).toBeInTheDocument()
            expect(screen.getByText('Track escalation patterns and team performance')).toBeInTheDocument()
        })

        it('should render refresh button', () => {
            renderSection(
                <EscalationSLASection
                    avgEscalationDurationByTag={mockAvgEscalationDurationByTag}
                    escalationPercentageByTag={mockEscalationPercentageByTag}
                    escalationTrendsByDate={mockEscalationTrendsByDate}
                    escalationsByTeam={mockEscalationsByTeam}
                    escalationsByImpact={mockEscalationsByImpact}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            expect(screen.getByTestId('refresh-button')).toBeInTheDocument()
        })

        it('should render Average Escalation Duration chart', () => {
            renderSection(
                <EscalationSLASection
                    avgEscalationDurationByTag={mockAvgEscalationDurationByTag}
                    escalationPercentageByTag={mockEscalationPercentageByTag}
                    escalationTrendsByDate={mockEscalationTrendsByDate}
                    escalationsByTeam={mockEscalationsByTeam}
                    escalationsByImpact={mockEscalationsByImpact}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            expect(screen.getByText('Average Escalation Duration by Tag (Top 15)')).toBeInTheDocument()
        })

        it('should render Escalation Count by Tag chart', () => {
            renderSection(
                <EscalationSLASection
                    avgEscalationDurationByTag={mockAvgEscalationDurationByTag}
                    escalationPercentageByTag={mockEscalationPercentageByTag}
                    escalationTrendsByDate={mockEscalationTrendsByDate}
                    escalationsByTeam={mockEscalationsByTeam}
                    escalationsByImpact={mockEscalationsByImpact}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            expect(screen.getByText('Escalation Count by Tag (Top 15)')).toBeInTheDocument()
        })

        it('should render Escalation Trends Over Time chart', () => {
            renderSection(
                <EscalationSLASection
                    avgEscalationDurationByTag={mockAvgEscalationDurationByTag}
                    escalationPercentageByTag={mockEscalationPercentageByTag}
                    escalationTrendsByDate={mockEscalationTrendsByDate}
                    escalationsByTeam={mockEscalationsByTeam}
                    escalationsByImpact={mockEscalationsByImpact}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            expect(screen.getByText('Escalation Trends Over Time')).toBeInTheDocument()
        })

        it('should render Escalations by Team chart', () => {
            renderSection(
                <EscalationSLASection
                    avgEscalationDurationByTag={mockAvgEscalationDurationByTag}
                    escalationPercentageByTag={mockEscalationPercentageByTag}
                    escalationTrendsByDate={mockEscalationTrendsByDate}
                    escalationsByTeam={mockEscalationsByTeam}
                    escalationsByImpact={mockEscalationsByImpact}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            expect(screen.getByText('Escalations by Team (Top 10)')).toBeInTheDocument()
        })

        it('should render Escalations by Impact chart', () => {
            renderSection(
                <EscalationSLASection
                    avgEscalationDurationByTag={mockAvgEscalationDurationByTag}
                    escalationPercentageByTag={mockEscalationPercentageByTag}
                    escalationTrendsByDate={mockEscalationTrendsByDate}
                    escalationsByTeam={mockEscalationsByTeam}
                    escalationsByImpact={mockEscalationsByImpact}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            expect(screen.getByText('Escalations by Impact Level')).toBeInTheDocument()
        })
    })

    describe('Empty States', () => {
        it('should handle empty avgEscalationDurationByTag', () => {
            renderSection(
                <EscalationSLASection
                    avgEscalationDurationByTag={[]}
                    escalationPercentageByTag={mockEscalationPercentageByTag}
                    escalationTrendsByDate={mockEscalationTrendsByDate}
                    escalationsByTeam={mockEscalationsByTeam}
                    escalationsByImpact={mockEscalationsByImpact}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            // Should show 0 data items for that chart
            const charts = screen.getAllByTestId('horizontal-bar-chart')
            expect(charts[0]).toHaveTextContent('Data items: 0')
        })

        it('should handle undefined data gracefully', () => {
            renderSection(
                <EscalationSLASection
                    avgEscalationDurationByTag={undefined}
                    escalationPercentageByTag={undefined}
                    escalationTrendsByDate={undefined}
                    escalationsByTeam={undefined}
                    escalationsByImpact={undefined}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            // All charts should still render but with 0 data
            const horizontalCharts = screen.getAllByTestId('horizontal-bar-chart')
            horizontalCharts.forEach(chart => {
                expect(chart).toHaveTextContent('Data items: 0')
            })
        })

        it('should handle empty escalation trends', () => {
            renderSection(
                <EscalationSLASection
                    avgEscalationDurationByTag={mockAvgEscalationDurationByTag}
                    escalationPercentageByTag={mockEscalationPercentageByTag}
                    escalationTrendsByDate={[]}
                    escalationsByTeam={mockEscalationsByTeam}
                    escalationsByImpact={mockEscalationsByImpact}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            // TimeSeriesChart should show 0 data points
            expect(screen.getByText('Data points: 0')).toBeInTheDocument()
        })

        it('should handle empty escalations by team', () => {
            renderSection(
                <EscalationSLASection
                    avgEscalationDurationByTag={mockAvgEscalationDurationByTag}
                    escalationPercentageByTag={mockEscalationPercentageByTag}
                    escalationTrendsByDate={mockEscalationTrendsByDate}
                    escalationsByTeam={[]}
                    escalationsByImpact={mockEscalationsByImpact}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            // Should render BarChart (for impact) - team chart is HorizontalBarChart
            const barChart = screen.getByTestId('bar-chart')
            expect(barChart).toBeInTheDocument()
        })
    })

    describe('Refresh Functionality', () => {
        it('should call onRefresh when refresh button is clicked', () => {
            renderSection(
                <EscalationSLASection
                    avgEscalationDurationByTag={mockAvgEscalationDurationByTag}
                    escalationPercentageByTag={mockEscalationPercentageByTag}
                    escalationTrendsByDate={mockEscalationTrendsByDate}
                    escalationsByTeam={mockEscalationsByTeam}
                    escalationsByImpact={mockEscalationsByImpact}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            const refreshButton = screen.getByTestId('refresh-button')
            refreshButton.click()

            expect(mockOnRefresh).toHaveBeenCalledTimes(1)
        })

        it('should show refreshing state', () => {
            renderSection(
                <EscalationSLASection
                    avgEscalationDurationByTag={mockAvgEscalationDurationByTag}
                    escalationPercentageByTag={mockEscalationPercentageByTag}
                    escalationTrendsByDate={mockEscalationTrendsByDate}
                    escalationsByTeam={mockEscalationsByTeam}
                    escalationsByImpact={mockEscalationsByImpact}
                    isRefreshing={true}
                    onRefresh={mockOnRefresh}
                />
            )

            expect(screen.getByText('Refreshing...')).toBeInTheDocument()
        })
    })

    describe('Data Integrity', () => {
        it('should render all 5 charts with data', () => {
            renderSection(
                <EscalationSLASection
                    avgEscalationDurationByTag={mockAvgEscalationDurationByTag}
                    escalationPercentageByTag={mockEscalationPercentageByTag}
                    escalationTrendsByDate={mockEscalationTrendsByDate}
                    escalationsByTeam={mockEscalationsByTeam}
                    escalationsByImpact={mockEscalationsByImpact}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            // Should have 3 HorizontalBarCharts (duration, count, team)
            expect(screen.getAllByTestId('horizontal-bar-chart')).toHaveLength(3)
            
            // Should have 1 TimeSeriesChart (trends)
            expect(screen.getByTestId('time-series-chart')).toBeInTheDocument()
            
            // Should have 1 regular BarChart (impact)
            expect(screen.getByTestId('bar-chart')).toBeInTheDocument()
        })

        it('should handle large datasets', () => {
            const largeTags = Array.from({ length: 20 }, (_, i) => ({
                tag: `tag-${i}`,
                avgDuration: Math.random() * 24 * 3600, // Random duration in seconds (up to 24 hours)
            }))

            renderSection(
                <EscalationSLASection
                    avgEscalationDurationByTag={largeTags}
                    escalationPercentageByTag={mockEscalationPercentageByTag}
                    escalationTrendsByDate={mockEscalationTrendsByDate}
                    escalationsByTeam={mockEscalationsByTeam}
                    escalationsByImpact={mockEscalationsByImpact}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            // Should show 20 data items
            const firstChart = screen.getAllByTestId('horizontal-bar-chart')[0]
            expect(firstChart).toHaveTextContent('Data items: 20')
        })

        it('should display correct data counts', () => {
            renderSection(
                <EscalationSLASection
                    avgEscalationDurationByTag={mockAvgEscalationDurationByTag}
                    escalationPercentageByTag={mockEscalationPercentageByTag}
                    escalationTrendsByDate={mockEscalationTrendsByDate}
                    escalationsByTeam={mockEscalationsByTeam}
                    escalationsByImpact={mockEscalationsByImpact}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            // Duration chart: 3 items
            // Count chart: 3 items
            // Team chart: 3 items
            const horizontalCharts = screen.getAllByTestId('horizontal-bar-chart')
            expect(horizontalCharts[0]).toHaveTextContent('Data items: 3')
            expect(horizontalCharts[1]).toHaveTextContent('Data items: 3')
            expect(horizontalCharts[2]).toHaveTextContent('Data items: 3')
        })
    })

    describe('Layout', () => {
        it('should render charts in responsive grid', () => {
            const { container } = renderSection(
                <EscalationSLASection
                    avgEscalationDurationByTag={mockAvgEscalationDurationByTag}
                    escalationPercentageByTag={mockEscalationPercentageByTag}
                    escalationTrendsByDate={mockEscalationTrendsByDate}
                    escalationsByTeam={mockEscalationsByTeam}
                    escalationsByImpact={mockEscalationsByImpact}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            // Should have grid containers
            const grids = container.querySelectorAll('.grid')
            expect(grids.length).toBeGreaterThanOrEqual(2)
        })

        it('should have proper spacing between sections', () => {
            const { container } = renderSection(
                <EscalationSLASection
                    avgEscalationDurationByTag={mockAvgEscalationDurationByTag}
                    escalationPercentageByTag={mockEscalationPercentageByTag}
                    escalationTrendsByDate={mockEscalationTrendsByDate}
                    escalationsByTeam={mockEscalationsByTeam}
                    escalationsByImpact={mockEscalationsByImpact}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            // Check for gap classes
            const grids = container.querySelectorAll('.grid')
            grids.forEach(grid => {
                expect(grid.className).toContain('gap-6')
            })
        })
    })
})

