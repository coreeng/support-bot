import { render } from '@testing-library/react'
import { HistogramChart } from '../HistogramChart'

jest.mock('recharts', () => ({
    ResponsiveContainer: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
    BarChart: ({ children }: { children: React.ReactNode }) => <div data-testid="bar-chart">{children}</div>,
    Bar: ({ fill }: { fill?: string }) => <div data-testid="bar" data-fill={fill} />,
    CartesianGrid: () => <div data-testid="grid" />,
    XAxis: () => <div data-testid="xaxis" />,
    YAxis: () => <div data-testid="yaxis" />,
    Tooltip: () => <div data-testid="tooltip" />,
}))

describe('HistogramChart', () => {
    const data = [
        { range: '0-10', count: 5 },
        { range: '10-20', count: 8 },
    ]

    it('uses var(--chart-1) as the default bar fill (no raw hex)', () => {
        const { getByTestId } = render(
            <HistogramChart title="Test" data={data} />
        )
        const bar = getByTestId('bar')
        expect(bar.getAttribute('data-fill')).toBe('var(--chart-1)')
    })

    it('honors a custom color when provided', () => {
        const { getByTestId } = render(
            <HistogramChart title="Test" data={data} color="var(--chart-3)" />
        )
        expect(getByTestId('bar').getAttribute('data-fill')).toBe('var(--chart-3)')
    })

    it('shows a loading state when isLoading is true', () => {
        const { getByText } = render(
            <HistogramChart title="Test" data={[]} isLoading={true} />
        )
        expect(getByText(/loading distribution data/i)).toBeInTheDocument()
    })
})
