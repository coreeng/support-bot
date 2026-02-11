// src/components/dashboards/__tests__/TimeSeriesChart.test.tsx
import { render, screen } from '@testing-library/react'
import { TimeSeriesChart } from '../TimeSeriesChart'

// Mock Recharts
jest.mock('recharts', () => ({
    ResponsiveContainer: ({ children }: any) => <div data-testid="responsive-container">{children}</div>,
    LineChart: ({ children, data }: any) => (
        <div data-testid="line-chart" data-length={data?.length}>
            {children}
        </div>
    ),
    Line: ({ dataKey, stroke, name }: any) => (
        <div data-testid={`line-${dataKey}`} data-stroke={stroke} data-name={name} />
    ),
    CartesianGrid: () => <div data-testid="grid" />,
    XAxis: ({ dataKey }: any) => <div data-testid="x-axis" data-key={dataKey} />,
    YAxis: ({ label }: any) => <div data-testid="y-axis" data-label={label?.value} />,
    Tooltip: () => <div data-testid="tooltip" />,
    Legend: () => <div data-testid="legend" />,
}))

describe('TimeSeriesChart', () => {
    const mockData = [
        { week: '2024-W01', opened: 10, closed: 8 },
        { week: '2024-W02', opened: 15, closed: 12 },
        { week: '2024-W03', opened: 20, closed: 18 },
    ]

    const mockLines = [
        { dataKey: 'opened', name: 'Opened', color: '#3b82f6' },
        { dataKey: 'closed', name: 'Closed', color: '#22c55e' },
    ]

    describe('Rendering', () => {
        it('should render chart title', () => {
            render(
                <TimeSeriesChart
                    title="Test Chart"
                    data={mockData}
                    lines={mockLines}
                    yAxisLabel="Tickets"
                />
            )

            expect(screen.getByText('Test Chart')).toBeInTheDocument()
        })

        it('should render all chart components', () => {
            render(
                <TimeSeriesChart
                    title="Test Chart"
                    data={mockData}
                    lines={mockLines}
                    yAxisLabel="Tickets"
                />
            )

            expect(screen.getByTestId('responsive-container')).toBeInTheDocument()
            expect(screen.getByTestId('line-chart')).toBeInTheDocument()
            expect(screen.getByTestId('grid')).toBeInTheDocument()
            expect(screen.getByTestId('x-axis')).toBeInTheDocument()
            expect(screen.getByTestId('y-axis')).toBeInTheDocument()
            expect(screen.getByTestId('tooltip')).toBeInTheDocument()
        })

        it('should render all lines', () => {
            render(
                <TimeSeriesChart
                    title="Test Chart"
                    data={mockData}
                    lines={mockLines}
                    yAxisLabel="Tickets"
                />
            )

            expect(screen.getByTestId('line-opened')).toBeInTheDocument()
            expect(screen.getByTestId('line-closed')).toBeInTheDocument()
        })
    })

    describe('Customization', () => {
        it('should use custom xAxisDataKey', () => {
            render(
                <TimeSeriesChart
                    title="Test Chart"
                    data={mockData}
                    lines={mockLines}
                    yAxisLabel="Tickets"
                    xAxisDataKey="time"
                />
            )

            const xAxis = screen.getByTestId('x-axis')
            expect(xAxis).toHaveAttribute('data-key', 'time')
        })

        it('should use default xAxisDataKey when not provided', () => {
            render(
                <TimeSeriesChart
                    title="Test Chart"
                    data={mockData}
                    lines={mockLines}
                    yAxisLabel="Tickets"
                />
            )

            const xAxis = screen.getByTestId('x-axis')
            expect(xAxis).toHaveAttribute('data-key', 'week')
        })

        it('should show legend when showLegend is true', () => {
            render(
                <TimeSeriesChart
                    title="Test Chart"
                    data={mockData}
                    lines={mockLines}
                    yAxisLabel="Tickets"
                    showLegend={true}
                />
            )

            expect(screen.getByTestId('legend')).toBeInTheDocument()
        })

        it('should hide legend when showLegend is false', () => {
            render(
                <TimeSeriesChart
                    title="Test Chart"
                    data={mockData}
                    lines={mockLines}
                    yAxisLabel="Tickets"
                    showLegend={false}
                />
            )

            expect(screen.queryByTestId('legend')).not.toBeInTheDocument()
        })

        it('should apply correct colors to lines', () => {
            render(
                <TimeSeriesChart
                    title="Test Chart"
                    data={mockData}
                    lines={mockLines}
                    yAxisLabel="Tickets"
                />
            )

            const openedLine = screen.getByTestId('line-opened')
            const closedLine = screen.getByTestId('line-closed')

            expect(openedLine).toHaveAttribute('data-stroke', '#3b82f6')
            expect(closedLine).toHaveAttribute('data-stroke', '#22c55e')
        })
    })

    describe('Data Handling', () => {
        it('should handle empty data array', () => {
            render(
                <TimeSeriesChart
                    title="Empty Chart"
                    data={[]}
                    lines={mockLines}
                    yAxisLabel="Tickets"
                />
            )

            expect(screen.getByText('Empty Chart')).toBeInTheDocument()
        })

        it('should handle single data point', () => {
            const singlePoint = [{ week: '2024-W01', opened: 10, closed: 8 }]

            render(
                <TimeSeriesChart
                    title="Single Point"
                    data={singlePoint}
                    lines={mockLines}
                    yAxisLabel="Tickets"
                />
            )

            expect(screen.getByTestId('line-chart')).toHaveAttribute('data-length', '1')
        })

        it('should handle large datasets', () => {
            const largeData = Array.from({ length: 52 }, (_, i) => ({
                week: `2024-W${i + 1}`,
                opened: Math.floor(Math.random() * 50),
                closed: Math.floor(Math.random() * 50),
            }))

            render(
                <TimeSeriesChart
                    title="Large Dataset"
                    data={largeData}
                    lines={mockLines}
                    yAxisLabel="Tickets"
                />
            )

            expect(screen.getByTestId('line-chart')).toHaveAttribute('data-length', '52')
        })
    })

    describe('Multiple Lines', () => {
        it('should render multiple lines correctly', () => {
            const fourLines = [
                { dataKey: 'opened', name: 'Opened', color: '#3b82f6' },
                { dataKey: 'closed', name: 'Closed', color: '#22c55e' },
                { dataKey: 'escalated', name: 'Escalated', color: '#a855f7' },
                { dataKey: 'stale', name: 'Stale', color: '#f59e0b' },
            ]

            render(
                <TimeSeriesChart
                    title="Multiple Lines"
                    data={[
                        { week: '2024-W01', opened: 10, closed: 8, escalated: 2, stale: 1 },
                    ]}
                    lines={fourLines}
                    yAxisLabel="Tickets"
                />
            )

            expect(screen.getByTestId('line-opened')).toBeInTheDocument()
            expect(screen.getByTestId('line-closed')).toBeInTheDocument()
            expect(screen.getByTestId('line-escalated')).toBeInTheDocument()
            expect(screen.getByTestId('line-stale')).toBeInTheDocument()
        })
    })

    describe('Y-Axis Label', () => {
        it('should display y-axis label', () => {
            render(
                <TimeSeriesChart
                    title="Test Chart"
                    data={mockData}
                    lines={mockLines}
                    yAxisLabel="Tickets Count"
                />
            )

            const yAxis = screen.getByTestId('y-axis')
            expect(yAxis).toHaveAttribute('data-label', 'Tickets Count')
        })

        it('should handle empty y-axis label', () => {
            render(
                <TimeSeriesChart
                    title="Test Chart"
                    data={mockData}
                    lines={mockLines}
                    yAxisLabel=""
                />
            )

            const yAxis = screen.getByTestId('y-axis')
            expect(yAxis).toHaveAttribute('data-label', '')
        })
    })

    describe('Responsiveness', () => {
        it('should render inside ResponsiveContainer', () => {
            render(
                <TimeSeriesChart
                    title="Test Chart"
                    data={mockData}
                    lines={mockLines}
                    yAxisLabel="Tickets"
                />
            )

            const container = screen.getByTestId('responsive-container')
            const chart = screen.getByTestId('line-chart')

            expect(container).toContainElement(chart)
        })
    })
})

