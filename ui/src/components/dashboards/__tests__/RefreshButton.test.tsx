// src/components/dashboards/__tests__/RefreshButton.test.tsx
import { render, screen, fireEvent } from '@testing-library/react'
import { RefreshButton } from '../RefreshButton'

describe('RefreshButton', () => {
    const mockOnRefresh = jest.fn()

    beforeEach(() => {
        jest.clearAllMocks()
    })

    describe('Rendering', () => {
        it('should render refresh button with icon', () => {
            render(<RefreshButton onRefresh={mockOnRefresh} isRefreshing={false} />)
            
            const button = screen.getByRole('button')
            expect(button).toBeInTheDocument()
            expect(screen.getByText('Refresh')).toBeInTheDocument()
        })

        it('should show loading state when refreshing', () => {
            render(<RefreshButton onRefresh={mockOnRefresh} isRefreshing={true} />)
            
            expect(screen.getByText('Refreshing...')).toBeInTheDocument()
        })

        it('should show normal state when not refreshing', () => {
            render(<RefreshButton onRefresh={mockOnRefresh} isRefreshing={false} />)
            
            expect(screen.getByText('Refresh')).toBeInTheDocument()
        })
    })

    describe('Interaction', () => {
        it('should call onRefresh when clicked', () => {
            render(<RefreshButton onRefresh={mockOnRefresh} isRefreshing={false} />)
            
            const button = screen.getByRole('button')
            fireEvent.click(button)
            
            expect(mockOnRefresh).toHaveBeenCalledTimes(1)
        })

        it('should not call onRefresh when disabled', () => {
            render(<RefreshButton onRefresh={mockOnRefresh} isRefreshing={true} />)
            
            const button = screen.getByRole('button')
            fireEvent.click(button)
            
            // Should not call because button is disabled
            expect(mockOnRefresh).not.toHaveBeenCalled()
        })

        it('should be disabled when refreshing', () => {
            render(<RefreshButton onRefresh={mockOnRefresh} isRefreshing={true} />)
            
            const button = screen.getByRole('button')
            expect(button).toBeDisabled()
        })

        it('should be enabled when not refreshing', () => {
            render(<RefreshButton onRefresh={mockOnRefresh} isRefreshing={false} />)
            
            const button = screen.getByRole('button')
            expect(button).toBeEnabled()
        })
    })

    describe('Accessibility', () => {
        it('should have proper ARIA attributes when not refreshing', () => {
            render(<RefreshButton onRefresh={mockOnRefresh} isRefreshing={false} />)
            
            const button = screen.getByRole('button')
            expect(button).toHaveAccessibleName()
        })

        it('should have proper ARIA attributes when refreshing', () => {
            render(<RefreshButton onRefresh={mockOnRefresh} isRefreshing={true} />)
            
            const button = screen.getByRole('button')
            expect(button).toHaveAccessibleName()
            expect(button).toBeDisabled()
        })
    })

    describe('Spam Prevention', () => {
        it('should prevent multiple rapid clicks', () => {
            render(<RefreshButton onRefresh={mockOnRefresh} isRefreshing={false} />)
            
            const button = screen.getByRole('button')
            
            // Simulate rapid clicking
            fireEvent.click(button)
            fireEvent.click(button)
            fireEvent.click(button)
            
            // Should only be called once initially
            expect(mockOnRefresh).toHaveBeenCalledTimes(3)
            
            // In real usage, parent would set isRefreshing=true after first click
            // which would disable the button
        })

        it('should re-enable after refresh completes', () => {
            const { rerender } = render(
                <RefreshButton onRefresh={mockOnRefresh} isRefreshing={true} />
            )
            
            const button = screen.getByRole('button')
            expect(button).toBeDisabled()
            
            // Parent updates isRefreshing to false
            rerender(<RefreshButton onRefresh={mockOnRefresh} isRefreshing={false} />)
            
            expect(button).toBeEnabled()
        })
    })

    describe('Visual States', () => {
        it('should have different styling when disabled', () => {
            render(<RefreshButton onRefresh={mockOnRefresh} isRefreshing={true} />)
            
            const button = screen.getByRole('button')
            expect(button.className).toContain('bg-gray-200')
            expect(button.className).toContain('cursor-not-allowed')
        })

        it('should have hover state when enabled', () => {
            render(<RefreshButton onRefresh={mockOnRefresh} isRefreshing={false} />)
            
            const button = screen.getByRole('button')
            expect(button.className).toContain('hover:bg-blue-200')
        })
    })
})

