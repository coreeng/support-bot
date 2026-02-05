import React from 'react'
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react'
import KnowledgeGapsPage from '../knowledge-gaps'

// Helper to wait for loading to finish
const waitForLoading = async () => {
    await waitFor(() => {
        expect(screen.queryByText(/Loading knowledge gaps/i)).not.toBeInTheDocument()
    }, { timeout: 3000 })
}

describe('KnowledgeGapsPage', () => {
    it('shows loading state initially', () => {
        render(<KnowledgeGapsPage />)
        expect(screen.getByText(/Loading knowledge gaps/i)).toBeInTheDocument()
    })

    it('renders support areas and knowledge gaps sections after loading', async () => {
        render(<KnowledgeGapsPage />)

        await waitForLoading()

        // Check for main headers
        expect(screen.getByText('Support Area Summary')).toBeInTheDocument()
        expect(screen.getByText('Support Areas')).toBeInTheDocument()
        expect(screen.getByText('Top Knowledge Gaps')).toBeInTheDocument()
    })

    it('displays correct query count and coverage', async () => {
        render(<KnowledgeGapsPage />)

        await waitForLoading()

        // Check for specific mock data values from Support Areas
        expect(screen.getByText('Knowledge Gap')).toBeInTheDocument()
        expect(screen.getByText('2127 Queries')).toBeInTheDocument()
        expect(screen.getByText('56% Coverage')).toBeInTheDocument()

        // Check for specific mock data values from Knowledge Gaps
        expect(screen.getByText('CI')).toBeInTheDocument()
        expect(screen.getByText('42 Queries')).toBeInTheDocument()
        expect(screen.getByText('75% Coverage')).toBeInTheDocument()
    })

    it('updates time period selection', async () => {
        render(<KnowledgeGapsPage />)

        await waitForLoading()

        const select = screen.getByRole('combobox') as HTMLSelectElement
        expect(select.value).toBe('Last Week')

        // Change selection
        fireEvent.change(select, { target: { value: 'Last Month' } })

        // Should go back to loading state briefly
        expect(screen.getByText(/Loading knowledge gaps/i)).toBeInTheDocument()

        await waitForLoading()

        expect(screen.getByText('Support Area Summary')).toBeInTheDocument()
    })

    it('toggles relevant queries visibility', async () => {
        render(<KnowledgeGapsPage />)

        await waitForLoading()

        // Queries should be hidden initially
        expect(screen.queryByText('"Documentation missing for new API"')).not.toBeInTheDocument()

        // Find the first expand button
        const toggleButtons = screen.getAllByText('Relevant Queries')
        fireEvent.click(toggleButtons[0])

        // Now it should be visible
        expect(screen.getByText('"Documentation missing for new API"')).toBeInTheDocument()

        // Click again to hide
        fireEvent.click(toggleButtons[0])

        // Should be hidden again (using waitFor because animation/state updates might clear it asynchronously)
        await waitFor(() => {
            expect(screen.queryByText('"Documentation missing for new API"')).not.toBeInTheDocument()
        })
    })

    it('sorts support areas and knowledge gaps by coverage percentage descending', async () => {
        render(<KnowledgeGapsPage />)
        await waitForLoading()

        // Helper to extract coverage percentage from text like "56% Coverage"
        const getCoverage = (text: string) => parseInt(text.replace('% Coverage', ''), 10)

        const coverageElements = screen.getAllByText(/% Coverage/i)
        const coverages = coverageElements.map(el => getCoverage(el.textContent || '0'))

        // First 5 are Support Areas, Next 5 are Top Knowledge Gaps
        const supportAreaCoverages = coverages.slice(0, 5)
        const knowledgeGapCoverages = coverages.slice(5)

        // Verify Support Areas sorted descending (56, 22, 13, 5, 4)
        expect(supportAreaCoverages).toEqual([56, 22, 13, 5, 4])

        // Verify Knowledge Gaps sorted descending (90, 88, 75, 60, 45)
        expect(knowledgeGapCoverages).toEqual([90, 88, 75, 60, 45])
    })
})
