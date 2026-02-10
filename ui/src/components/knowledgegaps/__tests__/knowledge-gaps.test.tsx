import React from 'react'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import KnowledgeGapsPage from '../knowledge-gaps'

// Helper to wait for loading to finish
const waitForLoading = async () => {
    await waitFor(() => {
        expect(screen.queryByText(/Loading support area summary/i)).not.toBeInTheDocument()
    }, { timeout: 3000 })
}

describe('KnowledgeGapsPage', () => {
    it('shows loading state initially', () => {
        render(<KnowledgeGapsPage />)
        expect(screen.getByText(/Loading support area summary/i)).toBeInTheDocument()
    })

    it('renders page header and collapsible sections after loading', async () => {
        render(<KnowledgeGapsPage />)
        await waitForLoading()

        // Check for main page header
        expect(screen.getByText('Support Area Summary')).toBeInTheDocument()
        expect(screen.getByText('Overview of support areas and knowledge gaps requiring attention')).toBeInTheDocument()

        // Check for collapsible section headers
        expect(screen.getByText('Top 5 Support Areas')).toBeInTheDocument()
        expect(screen.getByText('Top 5 Knowledge Gaps')).toBeInTheDocument()

        // Check overall coverage is displayed
        expect(screen.getByText('Overall Coverage')).toBeInTheDocument()
        expect(screen.getByText('83%')).toBeInTheDocument()
    })

    it('expands and collapses support areas section', async () => {
        render(<KnowledgeGapsPage />)
        await waitForLoading()

        // Initially collapsed - items should not be visible
        expect(screen.queryByText('Knowledge Gap')).not.toBeInTheDocument()

        // Click to expand Top 5 Support Areas
        const supportAreasButton = screen.getByRole('button', { name: /Top 5 Support Areas/i })
        fireEvent.click(supportAreasButton)

        // Now items should be visible
        expect(screen.getByText('Knowledge Gap')).toBeInTheDocument()
        expect(screen.getByText('2,127 queries')).toBeInTheDocument()
        expect(screen.getByText('56% coverage')).toBeInTheDocument()

        // Click again to collapse
        fireEvent.click(supportAreasButton)

        // Items should be hidden again
        await waitFor(() => {
            expect(screen.queryByText('Knowledge Gap')).not.toBeInTheDocument()
        })
    })

    it('expands and collapses knowledge gaps section', async () => {
        render(<KnowledgeGapsPage />)
        await waitForLoading()

        // Initially collapsed - items should not be visible
        expect(screen.queryByText('CI')).not.toBeInTheDocument()

        // Click to expand Top 5 Knowledge Gaps
        const knowledgeGapsButton = screen.getByRole('button', { name: /Top 5 Knowledge Gaps/i })
        fireEvent.click(knowledgeGapsButton)

        // Now items should be visible
        expect(screen.getByText('CI')).toBeInTheDocument()
        expect(screen.getByText('42 queries')).toBeInTheDocument()
        expect(screen.getByText('75% coverage')).toBeInTheDocument()
    })

    it('expands individual area to show relevant queries', async () => {
        render(<KnowledgeGapsPage />)
        await waitForLoading()

        // Expand the Top 5 Support Areas section first
        const supportAreasButton = screen.getByRole('button', { name: /Top 5 Support Areas/i })
        fireEvent.click(supportAreasButton)

        // Queries should not be visible yet
        expect(screen.queryByText('Documentation missing for new API')).not.toBeInTheDocument()

        // Find and click the expand button for "Knowledge Gap" area (first item)
        const expandButtons = screen.getAllByLabelText('Expand')
        fireEvent.click(expandButtons[0])

        // Now queries should be visible
        expect(screen.getByText('Relevant Support Queries')).toBeInTheDocument()
        expect(screen.getByText('Documentation missing for new API')).toBeInTheDocument()
        expect(screen.getByText('How to configure advanced settings?')).toBeInTheDocument()

        // Click to collapse
        const collapseButton = screen.getByLabelText('Collapse')
        fireEvent.click(collapseButton)

        // Queries should be hidden again
        await waitFor(() => {
            expect(screen.queryByText('Documentation missing for new API')).not.toBeInTheDocument()
        })
    })

    it('updates time period selection and reloads data', async () => {
        render(<KnowledgeGapsPage />)
        await waitForLoading()

        const select = screen.getByRole('combobox') as HTMLSelectElement
        expect(select.value).toBe('Last Week')

        // Change selection
        fireEvent.change(select, { target: { value: 'Last Month' } })

        // Should go back to loading state briefly
        expect(screen.getByText(/Loading support area summary/i)).toBeInTheDocument()

        await waitForLoading()

        // Should still show the main page
        expect(screen.getByText('Support Area Summary')).toBeInTheDocument()
        expect(select.value).toBe('Last Month')
    })

    it('displays all 5 items when sections are expanded', async () => {
        render(<KnowledgeGapsPage />)
        await waitForLoading()

        // Expand Top 5 Support Areas
        const supportAreasButton = screen.getByRole('button', { name: /Top 5 Support Areas/i })
        fireEvent.click(supportAreasButton)

        // Should show 5 numbered items
        expect(screen.getByText('Knowledge Gap')).toBeInTheDocument()
        expect(screen.getByText('Product Temporary Issue')).toBeInTheDocument()
        expect(screen.getByText('Task Request')).toBeInTheDocument()
        expect(screen.getByText('Product Usability Problem')).toBeInTheDocument()
        expect(screen.getByText('Feature Request')).toBeInTheDocument()

        // Expand Top 5 Knowledge Gaps
        const knowledgeGapsButton = screen.getByRole('button', { name: /Top 5 Knowledge Gaps/i })
        fireEvent.click(knowledgeGapsButton)

        // Should show 5 numbered items
        expect(screen.getByText('Connectivity and Networking')).toBeInTheDocument()
        expect(screen.getByText('Monitoring & Troubleshooting Tenant Applications')).toBeInTheDocument()
        expect(screen.getByText('CI')).toBeInTheDocument()
        expect(screen.getByText('Configuring Platform Features - Kafka and Dial')).toBeInTheDocument()
        expect(screen.getByText('Deploying & Configuring Tenant Applications')).toBeInTheDocument()
    })
})
