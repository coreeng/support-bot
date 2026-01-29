// src/components/dashboards/__tests__/ResponseSLASection.test.tsx
/* eslint-disable @typescript-eslint/no-explicit-any */
import { render, screen } from '@testing-library/react'
import { ResponseSLASection } from '../ResponseSLASection'

// Mock child components
jest.mock('../PercentileCard', () => ({
    PercentileCard: ({ title, p50, p90 }: any) => (
        <div data-testid="percentile-card">
            <h3>{title}</h3>
            <div>P50: {p50}</div>
            <div>P90: {p90}</div>
        </div>
    )
}))

jest.mock('../TimeBucketChart', () => ({
    TimeBucketChart: ({ title, data, isLoading }: any) => (
        <div data-testid="time-bucket-chart">
            <h3>{title}</h3>
            {isLoading ? <div>Loading chart...</div> : <div>Data points: {data?.length || 0}</div>}
        </div>
    )
}))

jest.mock('../MetricCard', () => ({
    MetricCard: ({ title, value, description, isLoading }: any) => (
        <div data-testid="metric-card">
            <h3>{title}</h3>
            {isLoading ? <div>Loading...</div> : <div>Value: {value}</div>}
            <p>{description}</p>
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

describe('ResponseSLASection', () => {
    // Mock values in SECONDS (as returned by the database)
    const mockFirstResponsePercentiles = { p50: 9000, p90: 30600 } // 2.5 hours, 8.5 hours
    const mockDurationDistribution = [45, 90, 120, 180, 300, 450, 600, 900] // seconds
    const mockUnattendedQueries = { count: 5 }
    const mockOnRefresh = jest.fn()

    beforeEach(() => {
        jest.clearAllMocks()
    })

    describe('Rendering', () => {
        it('should render section header', () => {
            render(
                <ResponseSLASection
                    firstResponsePercentiles={mockFirstResponsePercentiles}
                    durationDistribution={mockDurationDistribution}
                    unattendedQueries={mockUnattendedQueries}
                    isDistributionLoading={false}
                    isUnattendedLoading={false}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            expect(screen.getByText('Response Performance')).toBeInTheDocument()
            expect(screen.getByText('Track first response times and unattended queries')).toBeInTheDocument()
        })

        it('should render all three components', () => {
            render(
                <ResponseSLASection
                    firstResponsePercentiles={mockFirstResponsePercentiles}
                    durationDistribution={mockDurationDistribution}
                    unattendedQueries={mockUnattendedQueries}
                    isDistributionLoading={false}
                    isUnattendedLoading={false}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            expect(screen.getByTestId('percentile-card')).toBeInTheDocument()
            expect(screen.getByTestId('metric-card')).toBeInTheDocument()
            expect(screen.getByTestId('time-bucket-chart')).toBeInTheDocument()
        })

        it('should render refresh button', () => {
            render(
                <ResponseSLASection
                    firstResponsePercentiles={mockFirstResponsePercentiles}
                    durationDistribution={mockDurationDistribution}
                    unattendedQueries={mockUnattendedQueries}
                    isDistributionLoading={false}
                    isUnattendedLoading={false}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            expect(screen.getByTestId('refresh-button')).toBeInTheDocument()
        })

        it('should pass correct title to PercentileCard', () => {
            render(
                <ResponseSLASection
                    firstResponsePercentiles={mockFirstResponsePercentiles}
                    durationDistribution={mockDurationDistribution}
                    unattendedQueries={mockUnattendedQueries}
                    isDistributionLoading={false}
                    isUnattendedLoading={false}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            expect(screen.getByText('Time to First Response')).toBeInTheDocument()
        })

        it('should pass correct title to MetricCard', () => {
            render(
                <ResponseSLASection
                    firstResponsePercentiles={mockFirstResponsePercentiles}
                    durationDistribution={mockDurationDistribution}
                    unattendedQueries={mockUnattendedQueries}
                    isDistributionLoading={false}
                    isUnattendedLoading={false}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            expect(screen.getByText('Total Unattended Queries')).toBeInTheDocument()
            expect(screen.getByText('Queries without a corresponding ticket')).toBeInTheDocument()
        })
    })

    describe('Data Handling', () => {
        it('should handle undefined percentiles', () => {
            render(
                <ResponseSLASection
                    firstResponsePercentiles={undefined}
                    durationDistribution={mockDurationDistribution}
                    unattendedQueries={mockUnattendedQueries}
                    isDistributionLoading={false}
                    isUnattendedLoading={false}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            // Should render with default values (0)
            expect(screen.getByTestId('percentile-card')).toBeInTheDocument()
        })

        it('should handle undefined unattended queries', () => {
            render(
                <ResponseSLASection
                    firstResponsePercentiles={mockFirstResponsePercentiles}
                    durationDistribution={mockDurationDistribution}
                    unattendedQueries={undefined}
                    isDistributionLoading={false}
                    isUnattendedLoading={false}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            expect(screen.getByText('Value: 0')).toBeInTheDocument()
        })

        it('should handle undefined distribution data', () => {
            render(
                <ResponseSLASection
                    firstResponsePercentiles={mockFirstResponsePercentiles}
                    durationDistribution={undefined}
                    unattendedQueries={mockUnattendedQueries}
                    isDistributionLoading={false}
                    isUnattendedLoading={false}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            // Should render chart with 0 data points
            expect(screen.getByText('Data points: 0')).toBeInTheDocument()
        })

        it('should handle empty distribution array', () => {
            render(
                <ResponseSLASection
                    firstResponsePercentiles={mockFirstResponsePercentiles}
                    durationDistribution={[]}
                    unattendedQueries={mockUnattendedQueries}
                    isDistributionLoading={false}
                    isUnattendedLoading={false}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            expect(screen.getByTestId('time-bucket-chart')).toBeInTheDocument()
        })

        it('should display non-zero unattended queries count', () => {
            render(
                <ResponseSLASection
                    firstResponsePercentiles={mockFirstResponsePercentiles}
                    durationDistribution={mockDurationDistribution}
                    unattendedQueries={{ count: 42 }}
                    isDistributionLoading={false}
                    isUnattendedLoading={false}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            expect(screen.getByText('Value: 42')).toBeInTheDocument()
        })
    })

    describe('Loading States', () => {
        it('should show loading state for distribution chart', () => {
            render(
                <ResponseSLASection
                    firstResponsePercentiles={mockFirstResponsePercentiles}
                    durationDistribution={mockDurationDistribution}
                    unattendedQueries={mockUnattendedQueries}
                    isDistributionLoading={true}
                    isUnattendedLoading={false}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            expect(screen.getByText('Loading chart...')).toBeInTheDocument()
        })

        it('should show loading state for unattended queries', () => {
            render(
                <ResponseSLASection
                    firstResponsePercentiles={mockFirstResponsePercentiles}
                    durationDistribution={mockDurationDistribution}
                    unattendedQueries={mockUnattendedQueries}
                    isDistributionLoading={false}
                    isUnattendedLoading={true}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            // MetricCard should show loading
            const metricCard = screen.getByTestId('metric-card')
            expect(metricCard).toHaveTextContent('Loading...')
        })

        it('should show refreshing state', () => {
            render(
                <ResponseSLASection
                    firstResponsePercentiles={mockFirstResponsePercentiles}
                    durationDistribution={mockDurationDistribution}
                    unattendedQueries={mockUnattendedQueries}
                    isDistributionLoading={false}
                    isUnattendedLoading={false}
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
                <ResponseSLASection
                    firstResponsePercentiles={mockFirstResponsePercentiles}
                    durationDistribution={mockDurationDistribution}
                    unattendedQueries={mockUnattendedQueries}
                    isDistributionLoading={false}
                    isUnattendedLoading={false}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            const refreshButton = screen.getByTestId('refresh-button')
            refreshButton.click()

            expect(mockOnRefresh).toHaveBeenCalledTimes(1)
        })

        it('should not call onRefresh when disabled', () => {
            render(
                <ResponseSLASection
                    firstResponsePercentiles={mockFirstResponsePercentiles}
                    durationDistribution={mockDurationDistribution}
                    unattendedQueries={mockUnattendedQueries}
                    isDistributionLoading={false}
                    isUnattendedLoading={false}
                    isRefreshing={true}
                    onRefresh={mockOnRefresh}
                />
            )

            const refreshButton = screen.getByTestId('refresh-button')
            refreshButton.click()

            // Should not call due to disabled state
            expect(mockOnRefresh).not.toHaveBeenCalled()
        })
    })

    describe('Layout', () => {
        it('should render cards in responsive grid', () => {
            render(
                <ResponseSLASection
                    firstResponsePercentiles={mockFirstResponsePercentiles}
                    durationDistribution={mockDurationDistribution}
                    unattendedQueries={mockUnattendedQueries}
                    isDistributionLoading={false}
                    isUnattendedLoading={false}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            // Check that cards are rendered (implies grid layout exists)
            expect(screen.getByTestId('percentile-card')).toBeInTheDocument()
            expect(screen.getByTestId('metric-card')).toBeInTheDocument()
            
            // Grid container exists with proper classes
            const grid = document.querySelector('.grid')
            expect(grid).toBeInTheDocument()
            expect(grid?.className).toContain('grid-cols-1')
            expect(grid?.className).toContain('lg:grid-cols-2')
        })

        it('should render chart below cards', () => {
            render(
                <ResponseSLASection
                    firstResponsePercentiles={mockFirstResponsePercentiles}
                    durationDistribution={mockDurationDistribution}
                    unattendedQueries={mockUnattendedQueries}
                    isDistributionLoading={false}
                    isUnattendedLoading={false}
                    isRefreshing={false}
                    onRefresh={mockOnRefresh}
                />
            )

            const cards = screen.getByTestId('percentile-card')
            const chart = screen.getByTestId('time-bucket-chart')

            // Chart should appear after cards in DOM
            expect(cards.compareDocumentPosition(chart) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
        })
    })
})

