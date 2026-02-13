import React from 'react'
import { render, screen, fireEvent } from '@testing-library/react'
import KnowledgeGapsPage from '../knowledge-gaps'
import * as hooks from '../../../lib/hooks'
import { ToastProvider } from '@/components/ui/toast'

// Mock the hooks
jest.mock('../../../lib/hooks')

// Helper to render with ToastProvider
const renderWithToast = (component: React.ReactElement) => {
    return render(<ToastProvider>{component}</ToastProvider>)
}

const mockUseAnalysis = hooks.useAnalysis as jest.MockedFunction<typeof hooks.useAnalysis>

const mockAnalysisData = {
    knowledgeGaps: [
        {
            name: 'Connectivity and Networking',
            coveragePercentage: 90,
            queryCount: 28,
            queries: [
                { text: 'Firewall rules for output traffic', link: 'https://slack.com/1' },
                { text: 'DNS resolution issues', link: 'https://slack.com/2' }
            ]
        },
        {
            name: 'Monitoring & Troubleshooting Tenant Applications',
            coveragePercentage: 88,
            queryCount: 50,
            queries: [
                { text: 'How to view application logs?', link: 'https://slack.com/3' },
                { text: 'Setting up custom metrics', link: 'https://slack.com/4' }
            ]
        },
        {
            name: 'CI',
            coveragePercentage: 75,
            queryCount: 42,
            queries: [
                { text: 'How do I fix the CI pipeline failure?', link: 'https://slack.com/5' },
                { text: 'What is the correct configuration for the build step?', link: 'https://slack.com/6' }
            ]
        },
        {
            name: 'Configuring Platform Features - Kafka and Dial',
            coveragePercentage: 60,
            queryCount: 35,
            queries: [
                { text: 'How to setup Kafka consumers?', link: 'https://slack.com/7' },
                { text: 'Dial configuration for new tenant', link: 'https://slack.com/8' }
            ]
        },
        {
            name: 'Deploying & Configuring Tenant Applications',
            coveragePercentage: 45,
            queryCount: 15,
            queries: [
                { text: 'Deployment failed with timeout', link: 'https://slack.com/9' },
                { text: 'Configuring environment variables', link: 'https://slack.com/10' }
            ]
        }
    ],
    supportAreas: [
        {
            name: 'Knowledge Gap',
            coveragePercentage: 56,
            queryCount: 2127,
            queries: [
                { text: 'Documentation missing for new API', link: 'https://slack.com/11' },
                { text: 'How to configure advanced settings?', link: 'https://slack.com/12' }
            ]
        },
        {
            name: 'Product Temporary Issue',
            coveragePercentage: 22,
            queryCount: 825,
            queries: [
                { text: 'Service temporarily unavailable', link: 'https://slack.com/13' },
                { text: '503 errors on login', link: 'https://slack.com/14' }
            ]
        },
        {
            name: 'Task Request',
            coveragePercentage: 13,
            queryCount: 493,
            queries: [
                { text: 'Please reset my API key', link: 'https://slack.com/15' },
                { text: 'Update billing address', link: 'https://slack.com/16' }
            ]
        },
        {
            name: 'Product Usability Problem',
            coveragePercentage: 5,
            queryCount: 196,
            queries: [
                { text: 'Cannot find the logout button', link: 'https://slack.com/17' },
                { text: 'Dashboard is confusing', link: 'https://slack.com/18' }
            ]
        },
        {
            name: 'Feature Request',
            coveragePercentage: 4,
            queryCount: 163,
            queries: [
                { text: 'Add dark mode support', link: 'https://slack.com/19' },
                { text: 'Export report to PDF', link: 'https://slack.com/20' }
            ]
        }
    ]
}

describe('KnowledgeGapsPage', () => {
    beforeEach(() => {
        jest.clearAllMocks()
    })

    it('shows loading state initially', () => {
        mockUseAnalysis.mockReturnValue({
            data: undefined,
            isLoading: true,
            error: null
        } as any)

        renderWithToast(<KnowledgeGapsPage />)
        expect(screen.getByText(/Loading support area summary/i)).toBeInTheDocument()
    })

    it('shows error state when API fails', () => {
        mockUseAnalysis.mockReturnValue({
            data: undefined,
            isLoading: false,
            error: new Error('API Error')
        } as any)

        renderWithToast(<KnowledgeGapsPage />)
        expect(screen.getByText('Error loading analysis data')).toBeInTheDocument()
        expect(screen.getByText('Please try again later')).toBeInTheDocument()
    })

    it('renders page header and collapsible sections after loading', () => {
        mockUseAnalysis.mockReturnValue({
            data: mockAnalysisData,
            isLoading: false,
            error: null
        } as any)

        renderWithToast(<KnowledgeGapsPage />)

        // Check for main page header
        expect(screen.getByText('Support Area Summary')).toBeInTheDocument()
        expect(screen.getByText('Overview of support areas and knowledge gaps requiring attention')).toBeInTheDocument()

        // Check for import and export buttons
        expect(screen.getByText('Import Data')).toBeInTheDocument()
        expect(screen.getByText('Export Data')).toBeInTheDocument()

        // Check for collapsible section headers
        expect(screen.getByText('Top 5 Support Areas')).toBeInTheDocument()
        expect(screen.getByText('Top 5 Knowledge Gaps')).toBeInTheDocument()
    })

    it('expands and collapses support areas section', () => {
        mockUseAnalysis.mockReturnValue({
            data: mockAnalysisData,
            isLoading: false,
            error: null
        } as any)

        renderWithToast(<KnowledgeGapsPage />)

        // Initially collapsed - items should not be visible
        expect(screen.queryByText('Knowledge Gap')).not.toBeInTheDocument()

        // Click to expand Top 5 Support Areas
        const supportAreasButton = screen.getByRole('button', { name: /Top 5 Support Areas/i })
        fireEvent.click(supportAreasButton)

        // Now items should be visible
        expect(screen.getByText('Knowledge Gap')).toBeInTheDocument()
        expect(screen.getByText('2,127 queries')).toBeInTheDocument()

        // Click again to collapse
        fireEvent.click(supportAreasButton)

        // Items should be hidden again
        expect(screen.queryByText('Knowledge Gap')).not.toBeInTheDocument()
    })

    it('expands and collapses knowledge gaps section', () => {
        mockUseAnalysis.mockReturnValue({
            data: mockAnalysisData,
            isLoading: false,
            error: null
        } as any)

        renderWithToast(<KnowledgeGapsPage />)

        // Initially collapsed - items should not be visible
        expect(screen.queryByText('CI')).not.toBeInTheDocument()

        // Click to expand Top 5 Knowledge Gaps
        const knowledgeGapsButton = screen.getByRole('button', { name: /Top 5 Knowledge Gaps/i })
        fireEvent.click(knowledgeGapsButton)

        // Now items should be visible
        expect(screen.getByText('CI')).toBeInTheDocument()
        expect(screen.getByText('42 queries')).toBeInTheDocument()
    })

    it('expands individual area to show relevant queries', () => {
        mockUseAnalysis.mockReturnValue({
            data: mockAnalysisData,
            isLoading: false,
            error: null
        } as any)

        renderWithToast(<KnowledgeGapsPage />)

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
        expect(screen.queryByText('Documentation missing for new API')).not.toBeInTheDocument()
    })

    it('displays all 5 items when sections are expanded', () => {
        mockUseAnalysis.mockReturnValue({
            data: mockAnalysisData,
            isLoading: false,
            error: null
        } as any)

        renderWithToast(<KnowledgeGapsPage />)

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

    it('handles export button click', async () => {
        mockUseAnalysis.mockReturnValue({
            data: mockAnalysisData,
            isLoading: false,
            error: null
        } as any)

        // Mock fetch
        const mockFetch = jest.fn(() =>
            Promise.resolve({
                ok: true,
                blob: () => Promise.resolve(new Blob(['test'], { type: 'application/zip' }))
            } as Response)
        )
        global.fetch = mockFetch

        // Mock URL.createObjectURL and revokeObjectURL
        const mockCreateObjectURL = jest.fn(() => 'blob:test-url')
        const mockRevokeObjectURL = jest.fn()
        global.URL.createObjectURL = mockCreateObjectURL
        global.URL.revokeObjectURL = mockRevokeObjectURL

        // Mock HTMLAnchorElement click
        const mockClick = jest.fn()
        HTMLAnchorElement.prototype.click = mockClick

        renderWithToast(<KnowledgeGapsPage />)

        const exportButton = screen.getByText('Export Data')
        fireEvent.click(exportButton)

        // Wait for async operations
        await screen.findByText('Downloading...')

        // Verify fetch was called
        expect(mockFetch).toHaveBeenCalledWith('/api/summary-data/export?days=31')

        // Wait for button to return to normal state
        await screen.findByText('Export Data')

        // Verify download was triggered
        expect(mockClick).toHaveBeenCalled()
        expect(mockCreateObjectURL).toHaveBeenCalled()
        expect(mockRevokeObjectURL).toHaveBeenCalled()
    })

    it('handles import button click and file upload', async () => {
        mockUseAnalysis.mockReturnValue({
            data: mockAnalysisData,
            isLoading: false,
            error: null
        } as any)

        // Mock fetch for upload
        const mockFetch = jest.fn(() =>
            Promise.resolve({
                ok: true,
                json: () => Promise.resolve({ recordsImported: 42, message: 'Import successful' })
            } as Response)
        )
        global.fetch = mockFetch

        renderWithToast(<KnowledgeGapsPage />)

        const importButton = screen.getByText('Import Data')
        expect(importButton).toBeInTheDocument()

        // Simulate file selection
        const file = new File(['ticket_id\tDriver\tCategory\tFeature\tSummary'], 'analysis.tsv', { type: 'text/tab-separated-values' })
        const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement

        // Trigger file change
        Object.defineProperty(fileInput, 'files', {
            value: [file],
            writable: false,
        })
        fireEvent.change(fileInput)

        // Wait for async operations
        await screen.findByText('Uploading...')

        // Verify fetch was called with FormData
        expect(mockFetch).toHaveBeenCalledWith('/api/summary-data/import', expect.objectContaining({
            method: 'POST',
            body: expect.any(FormData)
        }))

        // Wait for button to return to normal state
        await screen.findByText('Import Data')

        // Verify success toast is shown
        expect(await screen.findByText('Import successful! 42 records imported.')).toBeInTheDocument()
    })
})
