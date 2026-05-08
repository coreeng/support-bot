import { render } from '@testing-library/react'
import { ScatterChart } from '../ScatterChart'

jest.mock('recharts', () => ({
    ResponsiveContainer: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
    ScatterChart: ({ children }: { children: React.ReactNode }) => <div data-testid="scatter-chart">{children}</div>,
    Scatter: ({ fill }: { fill?: string }) => <div data-testid="scatter" data-fill={fill} />,
    CartesianGrid: () => <div data-testid="grid" />,
    XAxis: () => <div data-testid="xaxis" />,
    YAxis: () => <div data-testid="yaxis" />,
    Tooltip: () => <div data-testid="tooltip" />,
}))

describe('ScatterChart', () => {
    const data = [
        { index: 1, value: 10 },
        { index: 2, value: 20 },
    ]

    it('uses var(--chart-1) as the default scatter fill (no raw hex)', () => {
        const { getByTestId } = render(
            <ScatterChart title="Test" data={data} />
        )
        expect(getByTestId('scatter').getAttribute('data-fill')).toBe('var(--chart-1)')
    })

    it('honors a custom color when provided', () => {
        const { getByTestId } = render(
            <ScatterChart title="Test" data={data} color="var(--chart-5)" />
        )
        expect(getByTestId('scatter').getAttribute('data-fill')).toBe('var(--chart-5)')
    })

    it('shows a loading state when isLoading is true', () => {
        const { getByText } = render(
            <ScatterChart title="Test" data={[]} isLoading={true} />
        )
        expect(getByText(/loading distribution data/i)).toBeInTheDocument()
    })
})
