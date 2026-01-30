// src/components/dashboards/__tests__/MetricCard.test.tsx
import { render, screen } from '@testing-library/react'
import { MetricCard } from '../MetricCard'

describe('MetricCard', () => {
    describe('Rendering', () => {
        it('should render title and value', () => {
            render(<MetricCard title="Test Metric" value={42} />)

            expect(screen.getByText('Test Metric')).toBeInTheDocument()
            expect(screen.getByText('42')).toBeInTheDocument()
        })

        it('should render string values', () => {
            render(<MetricCard title="Status" value="Active" />)

            expect(screen.getByText('Status')).toBeInTheDocument()
            expect(screen.getByText('Active')).toBeInTheDocument()
        })

        it('should render numeric values', () => {
            render(<MetricCard title="Count" value={1234} />)

            expect(screen.getByText('1234')).toBeInTheDocument()
        })

        it('should render description when provided', () => {
            render(
                <MetricCard 
                    title="Test Metric" 
                    value={42} 
                    description="This is a helpful description"
                />
            )

            expect(screen.getByText('This is a helpful description')).toBeInTheDocument()
        })

        it('should not render description when not provided', () => {
            const { container } = render(<MetricCard title="Test Metric" value={42} />)

            const descriptions = container.querySelectorAll('.text-sm.text-gray-600')
            expect(descriptions).toHaveLength(0)
        })
    })

    describe('Loading State', () => {
        it('should show loading text when isLoading is true', () => {
            render(<MetricCard title="Test Metric" value={42} isLoading={true} />)

            expect(screen.getByText('Loading...')).toBeInTheDocument()
        })

        it('should not show value when loading', () => {
            render(<MetricCard title="Test Metric" value={42} isLoading={true} />)

            expect(screen.queryByText('42')).not.toBeInTheDocument()
        })

        it('should show value when not loading', () => {
            render(<MetricCard title="Test Metric" value={42} isLoading={false} />)

            expect(screen.getByText('42')).toBeInTheDocument()
            expect(screen.queryByText('Loading...')).not.toBeInTheDocument()
        })

        it('should default to not loading when isLoading not provided', () => {
            render(<MetricCard title="Test Metric" value={42} />)

            expect(screen.getByText('42')).toBeInTheDocument()
            expect(screen.queryByText('Loading...')).not.toBeInTheDocument()
        })
    })

    describe('Color Schemes', () => {
        it('should apply blue color scheme by default', () => {
            const { container } = render(<MetricCard title="Test" value={42} />)

            const card = container.firstChild as HTMLElement
            expect(card.className).toContain('bg-blue-50')
            expect(card.className).toContain('border-blue-200')
        })

        it('should apply orange color scheme', () => {
            const { container } = render(<MetricCard title="Test" value={42} colorScheme="orange" />)

            const card = container.firstChild as HTMLElement
            expect(card.className).toContain('bg-orange-50')
            expect(card.className).toContain('border-orange-200')
        })

        it('should apply green color scheme', () => {
            const { container } = render(<MetricCard title="Test" value={42} colorScheme="green" />)

            const card = container.firstChild as HTMLElement
            expect(card.className).toContain('bg-green-50')
            expect(card.className).toContain('border-green-200')
        })

        it('should apply purple color scheme', () => {
            const { container } = render(<MetricCard title="Test" value={42} colorScheme="purple" />)

            const card = container.firstChild as HTMLElement
            expect(card.className).toContain('bg-purple-50')
            expect(card.className).toContain('border-purple-200')
        })

        it('should apply cyan color scheme', () => {
            const { container } = render(<MetricCard title="Test" value={42} colorScheme="cyan" />)

            const card = container.firstChild as HTMLElement
            expect(card.className).toContain('bg-cyan-50')
            expect(card.className).toContain('border-cyan-200')
        })
    })

    describe('Edge Cases', () => {
        it('should handle zero value', () => {
            render(<MetricCard title="Test" value={0} />)

            expect(screen.getByText('0')).toBeInTheDocument()
        })

        it('should handle negative values', () => {
            render(<MetricCard title="Test" value={-5} />)

            expect(screen.getByText('-5')).toBeInTheDocument()
        })

        it('should handle very large numbers', () => {
            render(<MetricCard title="Test" value={9999999} />)

            expect(screen.getByText('9999999')).toBeInTheDocument()
        })

        it('should handle empty string value', () => {
            render(<MetricCard title="Test" value="" />)

            // Empty string should render but be visually empty
            const card = screen.getByText('Test').parentElement
            expect(card).toBeInTheDocument()
        })

        it('should handle very long description', () => {
            const longDescription = 'This is a very long description that goes on and on and on and on and provides lots of context'

            render(<MetricCard title="Test" value={42} description={longDescription} />)

            expect(screen.getByText(longDescription)).toBeInTheDocument()
        })
    })

    describe('Styling', () => {
        it('should have rounded corners', () => {
            const { container } = render(<MetricCard title="Test" value={42} />)

            const card = container.firstChild as HTMLElement
            expect(card.className).toContain('rounded-xl')
        })

        it('should have padding', () => {
            const { container } = render(<MetricCard title="Test" value={42} />)

            const card = container.firstChild as HTMLElement
            expect(card.className).toContain('p-6')
        })

        it('should have border', () => {
            const { container } = render(<MetricCard title="Test" value={42} />)

            const card = container.firstChild as HTMLElement
            expect(card.className).toContain('border')
        })
    })
})

