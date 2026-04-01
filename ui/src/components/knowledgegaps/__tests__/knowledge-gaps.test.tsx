import React from 'react'
import {fireEvent, render, screen, waitFor} from '@testing-library/react'
import KnowledgeGapsPage from '../knowledge-gaps'
import * as hooks from '../../../lib/hooks'
import {ToastProvider} from '@/components/ui/toast'
import * as useAuthHook from '../../../hooks/useAuth'

const mockInvalidateQueries = jest.fn()

// Mock the hooks
jest.mock('../../../lib/hooks', () => ({
    useAnalysis: jest.fn(),
    apiFetch: jest.fn(),
}))
jest.mock('../../../hooks/useAuth')
jest.mock('../../tickets/EditTicketModal', () => ({
    __esModule: true,
    default: ({ ticketId, open, onSuccess }: { ticketId: string | null; open: boolean; onSuccess?: () => void }) => (
        open ? (
            <div data-testid="edit-ticket-modal">
                <span>Ticket modal for {ticketId}</span>
                <button type="button" onClick={onSuccess}>Trigger modal success</button>
            </div>
        ) : null
    )
}))

// Mock next-auth
jest.mock('next-auth/react', () => ({
    getCsrfToken: jest.fn(() => Promise.resolve('mock-csrf-token'))
}))

// Mock @tanstack/react-query
jest.mock('@tanstack/react-query', () => ({
    ...jest.requireActual('@tanstack/react-query'),
    useQueryClient: jest.fn(() => ({
        invalidateQueries: mockInvalidateQueries
    }))
}))

// Helper to render with ToastProvider
const renderWithToast = (component: React.ReactElement) => {
    return render(<ToastProvider>{component}</ToastProvider>)
}

const mockUseAnalysis = hooks.useAnalysis as jest.MockedFunction<typeof hooks.useAnalysis>
const mockApiFetch = hooks.apiFetch as jest.MockedFunction<typeof hooks.apiFetch>
const mockUseAuth = useAuthHook.useAuth as jest.MockedFunction<typeof useAuthHook.useAuth>

const mockAnalysisData = {
    knowledgeGaps: [
        {
            name: 'Connectivity and Networking',
            coveragePercentage: 90,
            queryCount: 28,
            queries: [
                { text: 'Firewall rules for output traffic', timestamp: '1743426900.000001', ticketId: '101', link: null },
                { text: 'DNS resolution issues', timestamp: '1743344400.000001', ticketId: '102', link: null }
            ]
        },
        {
            name: 'Monitoring & Troubleshooting Tenant Applications',
            coveragePercentage: 88,
            queryCount: 50,
            queries: [
                { text: 'How to view application logs?', timestamp: '1743242400.000001', ticketId: '103', link: null },
                { text: 'Setting up custom metrics', timestamp: '1743141900.000001', ticketId: '104', link: null }
            ]
        },
        {
            name: 'CI',
            coveragePercentage: 75,
            queryCount: 42,
            queries: [
                { text: 'How do I fix the CI pipeline failure?', timestamp: '1743075000.000001', ticketId: '105', link: null },
                { text: 'What is the correct configuration for the build step?', timestamp: '1743005400.000001', ticketId: '106', link: null }
            ]
        },
        {
            name: 'Configuring Platform Features - Kafka and Dial',
            coveragePercentage: 60,
            queryCount: 35,
            queries: [
                { text: 'How to setup Kafka consumers?', timestamp: '1742919000.000001', ticketId: '107', link: null },
                { text: 'Dial configuration for new tenant', timestamp: '1742838000.000001', ticketId: '108', link: null }
            ]
        },
        {
            name: 'Deploying & Configuring Tenant Applications',
            coveragePercentage: 45,
            queryCount: 15,
            queries: [
                { text: 'Deployment failed with timeout', timestamp: '1742732700.000001', ticketId: '109', link: null },
                { text: 'Configuring environment variables', timestamp: '1742680200.000001', ticketId: '110', link: null }
            ]
        }
    ],
    supportAreas: [
        {
            name: 'Knowledge Gap',
            coveragePercentage: 56,
            queryCount: 2127,
            queries: [
                { text: 'Documentation missing for new API', timestamp: '1743424496.000001', ticketId: '201', link: null },
                { text: 'How to configure advanced settings?', timestamp: '1743318489.000001', ticketId: '210', link: null }
            ]
        },
        {
            name: 'Product Temporary Issue',
            coveragePercentage: 22,
            queryCount: 825,
            queries: [
                { text: 'Service temporarily unavailable', timestamp: '1743228000.000001', ticketId: '202', link: null },
                { text: '503 errors on login', timestamp: '1743185700.000001', ticketId: '203', link: null }
            ]
        },
        {
            name: 'Task Request',
            coveragePercentage: 13,
            queryCount: 493,
            queries: [
                { text: 'Please reset my API key', timestamp: '1743087600.000001', ticketId: '204', link: null },
                { text: 'Update billing address', timestamp: '1742977800.000001', ticketId: '205', link: null }
            ]
        },
        {
            name: 'Product Usability Problem',
            coveragePercentage: 5,
            queryCount: 196,
            queries: [
                { text: 'Cannot find the logout button', timestamp: '1742901060.000001', ticketId: '206', link: null },
                { text: 'Dashboard is confusing', timestamp: '1742826120.000001', ticketId: '207', link: null }
            ]
        },
        {
            name: 'Feature Request',
            coveragePercentage: 4,
            queryCount: 163,
            queries: [
                { text: 'Add dark mode support', timestamp: '1742759100.000001', ticketId: '208', link: null },
                { text: 'Export report to PDF', timestamp: '1742677800.000001', ticketId: '209', link: null }
            ]
        }
    ]
}

describe('KnowledgeGapsPage', () => {
    beforeEach(() => {
        jest.clearAllMocks()
        mockInvalidateQueries.mockClear()
        // Default mock for useAuth - SUPPORT_ENGINEER role
        mockUseAuth.mockReturnValue({
            user: { id: '1', email: 'test@example.com', name: 'Test User', teams: [], roles: ['SUPPORT_ENGINEER'] },
            isLoading: false,
            isAuthenticated: true,
            isLeadership: false,
            isEscalationTeam: false,
            isSupportEngineer: true,
            actualEscalationTeams: [],
            logout: jest.fn()
        })

        // Default mock for apiFetch to handle /api/analysis/enabled and /api/analysis/status
        mockApiFetch.mockImplementation((url) => {
            if (url === '/api/analysis/enabled') {
                return Promise.resolve({
                    ok: true,
                    json: () => Promise.resolve({ enabled: true })
                } as Response)
            }
            if (url === '/api/analysis/status') {
                return Promise.resolve({
                    ok: true,
                    json: () => Promise.resolve({ running: false })
                } as Response)
            }
            return Promise.resolve({
                ok: true,
                json: () => Promise.resolve({})
            } as Response)
        })
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

        // Check for analysis trigger
        expect(screen.getByRole('button', { name: 'Run Analysis' })).toBeInTheDocument()

        // Check for collapsible section headers
        expect(screen.getByText('Top Support Areas')).toBeInTheDocument()
        expect(screen.getByText('Top Knowledge Gaps')).toBeInTheDocument()
    })

    it('sections are expanded by default and can be collapsed', () => {
        mockUseAnalysis.mockReturnValue({
            data: mockAnalysisData,
            isLoading: false,
            error: null
        } as any)

        renderWithToast(<KnowledgeGapsPage />)

        // Sections expanded by default - items should be visible
        expect(screen.getByText('Knowledge Gap')).toBeInTheDocument()
        expect(screen.getByText('2,127 total queries')).toBeInTheDocument()
        expect(screen.getByText('CI')).toBeInTheDocument()
        expect(screen.getByText('42 total queries')).toBeInTheDocument()

        // Collapse support areas
        const supportAreasButton = screen.getByRole('button', { name: /Top Support Areas/i })
        expect(supportAreasButton).toHaveAttribute('aria-expanded', 'true')
        expect(supportAreasButton).toHaveAttribute('aria-controls', 'support-areas-section')
        fireEvent.click(supportAreasButton)

        // List item hidden
        expect(supportAreasButton).toHaveAttribute('aria-expanded', 'false')
        expect(screen.queryByText('Knowledge Gap')).not.toBeInTheDocument()

        // Collapse knowledge gaps
        const knowledgeGapsButton = screen.getByRole('button', { name: /Top Knowledge Gaps/i })
        expect(knowledgeGapsButton).toHaveAttribute('aria-expanded', 'true')
        expect(knowledgeGapsButton).toHaveAttribute('aria-controls', 'knowledge-gaps-section')
        fireEvent.click(knowledgeGapsButton)

        expect(knowledgeGapsButton).toHaveAttribute('aria-expanded', 'false')
        expect(screen.queryByText('CI')).not.toBeInTheDocument()
    })

    it('expands individual area to show relevant queries on click', () => {
        mockUseAnalysis.mockReturnValue({
            data: mockAnalysisData,
            isLoading: false,
            error: null
        } as any)

        renderWithToast(<KnowledgeGapsPage />)

        // Individual items should NOT be auto-expanded
        expect(screen.queryByText('Documentation missing for new API')).not.toBeInTheDocument()

        // Click on "Knowledge Gap" area card to expand it
        const disclosure = screen.getAllByRole('button').find((button) =>
            button.getAttribute('aria-controls') === 'support-area:Knowledge Gap-queries'
        )
        expect(disclosure).toBeDefined()
        expect(disclosure).toHaveAttribute('aria-expanded', 'false')
        fireEvent.click(disclosure!)

        // Now queries should be visible
        expect(disclosure).toHaveAttribute('aria-expanded', 'true')
        expect(screen.getByText('Showing up to 5 most recent queries in this category')).toBeInTheDocument()
        expect(screen.getByText('Documentation missing for new API')).toBeInTheDocument()
        expect(screen.getByText('How to configure advanced settings?')).toBeInTheDocument()
        expect(screen.getByText('Mar 31, 2025, 12:34 PM')).toBeInTheDocument()
        expect(screen.getByText('Mar 30, 2025, 7:08 AM')).toBeInTheDocument()

        // Click again to collapse
        fireEvent.click(disclosure!)

        // Queries should be hidden
        expect(disclosure).toHaveAttribute('aria-expanded', 'false')
        expect(screen.queryByText('Documentation missing for new API')).not.toBeInTheDocument()
    })

    it('keeps expansion state scoped to each section when item names match', () => {
        const duplicatedNameData = {
            supportAreas: [
                {
                    name: 'Shared Topic',
                    coveragePercentage: 56,
                    queryCount: 12,
                    queries: [
                        { text: 'Support area query', timestamp: '1743424496.000001', ticketId: '401', link: null }
                    ]
                }
            ],
            knowledgeGaps: [
                {
                    name: 'Shared Topic',
                    coveragePercentage: 44,
                    queryCount: 8,
                    queries: [
                        { text: 'Knowledge gap query', timestamp: '1743318489.000001', ticketId: '402', link: null }
                    ]
                }
            ]
        }

        mockUseAnalysis.mockReturnValue({
            data: duplicatedNameData,
            isLoading: false,
            error: null
        } as any)

        renderWithToast(<KnowledgeGapsPage />)

        const disclosures = screen.getAllByRole('button', { name: /Shared Topic/ })
        expect(disclosures).toHaveLength(2)

        fireEvent.click(disclosures[0])

        expect(screen.getByText('Support area query')).toBeInTheDocument()
        expect(screen.queryByText('Knowledge gap query')).not.toBeInTheDocument()
        expect(disclosures[0]).toHaveAttribute('aria-expanded', 'true')
        expect(disclosures[1]).toHaveAttribute('aria-expanded', 'false')
    })

    it('opens the ticket modal when a summarized query with a ticket id is clicked', async () => {
        mockUseAnalysis.mockReturnValue({
            data: mockAnalysisData,
            isLoading: false,
            error: null
        } as any)

        renderWithToast(<KnowledgeGapsPage />)

        // Expand the "Knowledge Gap" support area to reveal queries
        fireEvent.click(screen.getByText('Knowledge Gap'))

        expect(screen.getByText('Mar 31, 2025, 12:34 PM')).toBeInTheDocument()
        fireEvent.click(screen.getByRole('button', { name: /documentation missing for new api/i }))

        await waitFor(() => {
            expect(screen.getByTestId('edit-ticket-modal')).toHaveTextContent('Ticket modal for 201')
        })
    })

    it('invalidates ticket and analysis queries when the ticket modal succeeds from this page', async () => {
        mockUseAnalysis.mockReturnValue({
            data: mockAnalysisData,
            isLoading: false,
            error: null
        } as any)

        renderWithToast(<KnowledgeGapsPage />)

        fireEvent.click(screen.getByText('Knowledge Gap'))
        fireEvent.click(screen.getByRole('button', { name: /documentation missing for new api/i }))

        await waitFor(() => {
            expect(screen.getByTestId('edit-ticket-modal')).toBeInTheDocument()
        })

        fireEvent.click(screen.getByRole('button', { name: /trigger modal success/i }))

        expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['ticket', '201'] })
        expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['tickets'] })
        expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['analysis'] })
    })

    it('renders all query rows as clickable buttons', () => {
        const dataWithQueries = {
            ...mockAnalysisData,
            supportAreas: [
                {
                    name: 'Mixed Links',
                    coveragePercentage: 50,
                    queryCount: 10,
                    queries: [
                        { text: 'Query with ticket', timestamp: '1743422400.000001', ticketId: '301', link: null },
                        { text: 'Another query', timestamp: '1743422460.000001', ticketId: '302', link: null }
                    ]
                }
            ]
        }

        mockUseAnalysis.mockReturnValue({
            data: dataWithQueries,
            isLoading: false,
            error: null
        } as any)

        renderWithToast(<KnowledgeGapsPage />)

        // Expand the item to reveal queries
        fireEvent.click(screen.getByText('Mixed Links'))

        expect(screen.getByRole('button', { name: /query with ticket/i })).toBeInTheDocument()
        expect(screen.getByRole('button', { name: /another query/i })).toBeInTheDocument()
    })

    it('renders invalid timestamps as raw strings instead of crashing', () => {
        const dataWithBadTimestamp = {
            ...mockAnalysisData,
            supportAreas: [
                {
                    name: 'Bad Timestamps',
                    coveragePercentage: 100,
                    queryCount: 1,
                    queries: [
                        { text: 'Query with bad timestamp', timestamp: 'not-a-date', ticketId: '999', link: null }
                    ]
                }
            ]
        }

        mockUseAnalysis.mockReturnValue({
            data: dataWithBadTimestamp,
            isLoading: false,
            error: null
        } as any)

        renderWithToast(<KnowledgeGapsPage />)

        fireEvent.click(screen.getByText('Bad Timestamps'))

        expect(screen.getByText('not-a-date')).toBeInTheDocument()
    })


    it('displays all 5 items in each section', () => {
        mockUseAnalysis.mockReturnValue({
            data: mockAnalysisData,
            isLoading: false,
            error: null
        } as any)

        renderWithToast(<KnowledgeGapsPage />)

        // Support areas - all 5 visible by default
        expect(screen.getByText('Knowledge Gap')).toBeInTheDocument()
        expect(screen.getByText('Product Temporary Issue')).toBeInTheDocument()
        expect(screen.getByText('Task Request')).toBeInTheDocument()
        expect(screen.getByText('Product Usability Problem')).toBeInTheDocument()
        expect(screen.getByText('Feature Request')).toBeInTheDocument()

        // Knowledge gaps - all 5 visible by default
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

        // Mock apiFetch for export
        mockApiFetch.mockImplementation((url) => {
            if (url === '/api/summary-data/export?days=7') {
                return Promise.resolve({
                    ok: true,
                    blob: () => Promise.resolve(new Blob(['test'], { type: 'application/zip' }))
                } as Response)
            }
            // Default for other calls
            return Promise.resolve({
                ok: true,
                json: () => Promise.resolve({ enabled: false })
            } as Response)
        })

        // Mock URL.createObjectURL and revokeObjectURL
        const mockCreateObjectURL = jest.fn(() => 'blob:test-url')
        const mockRevokeObjectURL = jest.fn()
        global.URL.createObjectURL = mockCreateObjectURL
        global.URL.revokeObjectURL = mockRevokeObjectURL

        // Mock HTMLAnchorElement click
        const mockClick = jest.fn()
        HTMLAnchorElement.prototype.click = mockClick

        renderWithToast(<KnowledgeGapsPage />)

        fireEvent.click(screen.getByRole('button', { name: 'Run Analysis' }))

        const exportButton = screen.getByText('Export')
        fireEvent.click(exportButton)

        // Verify apiFetch was called with default value of 7 days (Week)
        await waitFor(() => {
            expect(mockApiFetch).toHaveBeenCalledWith('/api/summary-data/export?days=7')
        })
        expect(screen.queryByText('Analysis settings')).not.toBeInTheDocument()

        // Verify download was triggered
        expect(mockClick).toHaveBeenCalled()
        expect(mockCreateObjectURL).toHaveBeenCalled()
        expect(mockRevokeObjectURL).toHaveBeenCalled()
    })

    it('renders time period dropdown with correct options', async () => {
        mockUseAnalysis.mockReturnValue({
            data: mockAnalysisData,
            isLoading: false,
            error: null
        } as any)

        renderWithToast(<KnowledgeGapsPage />)

        fireEvent.click(screen.getByRole('button', { name: 'Run Analysis' }))

        // Find the select dropdown - default is Week (7 days)
        const select = await screen.findByLabelText('Query window')
        expect(select).toBeInTheDocument()

        // Verify all options are present
        const options = screen.getAllByRole('option')
        expect(options).toHaveLength(3)
        expect(screen.getByRole('option', { name: 'Week' })).toBeInTheDocument()
        expect(screen.getByRole('option', { name: 'Month' })).toBeInTheDocument()
        expect(screen.getByRole('option', { name: 'Quarter' })).toBeInTheDocument()
    })

    it('uses selected time period when exporting data', async () => {
        mockUseAnalysis.mockReturnValue({
            data: mockAnalysisData,
            isLoading: false,
            error: null
        } as any)

        // Mock apiFetch
        mockApiFetch.mockImplementation((url) => {
            if (url === '/api/analysis/enabled') {
                return Promise.resolve({
                    ok: true,
                    json: () => Promise.resolve({ enabled: false })
                } as Response)
            }
            return Promise.resolve({
                ok: true,
                blob: () => Promise.resolve(new Blob(['test'], { type: 'application/zip' }))
            } as Response)
        })

        // Mock URL.createObjectURL and revokeObjectURL
        const mockCreateObjectURL = jest.fn(() => 'blob:test-url')
        const mockRevokeObjectURL = jest.fn()
        global.URL.createObjectURL = mockCreateObjectURL
        global.URL.revokeObjectURL = mockRevokeObjectURL

        // Mock HTMLAnchorElement click
      HTMLAnchorElement.prototype.click = jest.fn()

        renderWithToast(<KnowledgeGapsPage />)

        // Change the time period to Month (31 days)
        fireEvent.click(screen.getByRole('button', { name: 'Run Analysis' }))

        const select = await screen.findByLabelText('Query window')
        fireEvent.change(select, { target: { value: '31' } })

        // Click export button
        const exportButton = screen.getByText('Export')
        fireEvent.click(exportButton)

        // Wait for the fetch to be called
        await new Promise(resolve => setTimeout(resolve, 100))

        // Verify apiFetch was called with days=31
        expect(mockApiFetch).toHaveBeenCalledWith('/api/summary-data/export?days=31')
    })

    it('uses quarter time period when exporting data', async () => {
        mockUseAnalysis.mockReturnValue({
            data: mockAnalysisData,
            isLoading: false,
            error: null
        } as any)

        // Mock apiFetch
        mockApiFetch.mockImplementation((url) => {
            if (url === '/api/analysis/enabled') {
                return Promise.resolve({
                    ok: true,
                    json: () => Promise.resolve({ enabled: false })
                } as Response)
            }
            return Promise.resolve({
                ok: true,
                blob: () => Promise.resolve(new Blob(['test'], { type: 'application/zip' }))
            } as Response)
        })

        // Mock URL.createObjectURL and revokeObjectURL
        const mockCreateObjectURL = jest.fn(() => 'blob:test-url')
        const mockRevokeObjectURL = jest.fn()
        global.URL.createObjectURL = mockCreateObjectURL
        global.URL.revokeObjectURL = mockRevokeObjectURL

        // Mock HTMLAnchorElement click
      HTMLAnchorElement.prototype.click = jest.fn()

        renderWithToast(<KnowledgeGapsPage />)

        // Change the time period to Quarter (92 days)
        fireEvent.click(screen.getByRole('button', { name: 'Run Analysis' }))

        const select = await screen.findByLabelText('Query window')
        fireEvent.change(select, { target: { value: '92' } })

        // Click export button
        const exportButton = screen.getByText('Export')
        fireEvent.click(exportButton)

        // Wait for the fetch to be called
        await new Promise(resolve => setTimeout(resolve, 100))

        // Verify apiFetch was called with days=92
        expect(mockApiFetch).toHaveBeenCalledWith('/api/summary-data/export?days=92')
    })

    it('handles import button click and file upload', async () => {
        const { useQueryClient } = require('@tanstack/react-query')
        const mockInvalidateQueries = jest.fn()
        useQueryClient.mockReturnValue({
            invalidateQueries: mockInvalidateQueries
        })

        mockUseAnalysis.mockReturnValue({
            data: mockAnalysisData,
            isLoading: false,
            error: null
        } as any)

        // Mock apiFetch for upload
        mockApiFetch.mockImplementation((url, options) => {
            if (url === '/api/summary-data/import') {
                return Promise.resolve({
                    ok: true,
                    json: () => Promise.resolve({ recordsImported: 42, message: 'Import successful' })
                } as Response)
            }
            // Default for other calls
            return Promise.resolve({
                ok: true,
                json: () => Promise.resolve({ enabled: false })
            } as Response)
        })

        renderWithToast(<KnowledgeGapsPage />)

        fireEvent.click(screen.getByRole('button', { name: 'Run Analysis' }))

        const importButton = screen.getByText('Import')
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

        // Verify apiFetch was called with FormData
        expect(mockApiFetch).toHaveBeenCalledWith('/api/summary-data/import', expect.objectContaining({
            method: 'POST',
            body: expect.any(FormData)
        }))

        // Wait for button to return to normal state
        await screen.findByText('Import')

        // Verify success toast is shown
        expect(await screen.findByText('Import successful! 42 records imported.')).toBeInTheDocument()

        // Verify that invalidateQueries was called to refresh the data
        expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['analysis'] })
    })

    it('handles prompt button click to download analysis prompt', async () => {
        mockUseAnalysis.mockReturnValue({
            data: mockAnalysisData,
            isLoading: false,
            error: null
        } as any)

        // Mock apiFetch
        mockApiFetch.mockImplementation((url) => {
            if (url === '/api/summary-data/analysis') {
                return Promise.resolve({
                    ok: true,
                    blob: () => Promise.resolve(new Blob(['test prompt'], { type: 'application/zip' }))
                } as Response)
            }
            // Default for other calls
            return Promise.resolve({
                ok: true,
                json: () => Promise.resolve({ enabled: false })
            } as Response)
        })

        // Mock URL.createObjectURL and revokeObjectURL
        const mockCreateObjectURL = jest.fn(() => 'blob:test-url')
        const mockRevokeObjectURL = jest.fn()
        global.URL.createObjectURL = mockCreateObjectURL
        global.URL.revokeObjectURL = mockRevokeObjectURL

        // Mock HTMLAnchorElement click
        const mockClick = jest.fn()
        HTMLAnchorElement.prototype.click = mockClick

        renderWithToast(<KnowledgeGapsPage />)

        fireEvent.click(screen.getByRole('button', { name: 'Run Analysis' }))

        const promptButton = screen.getByText('Analysis Bundle')
        fireEvent.click(promptButton)

        // Wait for async operations
        await new Promise(resolve => setTimeout(resolve, 100))

        // Verify apiFetch was called
        expect(mockApiFetch).toHaveBeenCalledWith('/api/summary-data/analysis')

        // Verify download was triggered
        expect(mockClick).toHaveBeenCalled()
        expect(mockCreateObjectURL).toHaveBeenCalled()
        expect(mockRevokeObjectURL).toHaveBeenCalled()
    })

    describe('role-based button visibility', () => {
        beforeEach(() => {
            mockUseAnalysis.mockReturnValue({
                data: mockAnalysisData,
                isLoading: false,
                error: null
            } as any)
        })

        it('shows buttons when user has SUPPORT_ENGINEER role', () => {
            mockUseAuth.mockReturnValue({
                user: { id: '1', email: 'test@example.com', name: 'Test User', teams: [], roles: ['SUPPORT_ENGINEER'] },
                isLoading: false,
                isAuthenticated: true,
                isLeadership: false,
                isEscalationTeam: false,
                isSupportEngineer: true,
                actualEscalationTeams: [],
                logout: jest.fn()
            })

            renderWithToast(<KnowledgeGapsPage />)

            expect(screen.getByRole('button', { name: 'Run Analysis' })).toBeInTheDocument()
        })

        it('hides buttons when user does not have SUPPORT_ENGINEER role', () => {
            mockUseAuth.mockReturnValue({
                user: { id: '1', email: 'test@example.com', name: 'Test User', teams: [], roles: ['USER'] },
                isLoading: false,
                isAuthenticated: true,
                isLeadership: false,
                isEscalationTeam: false,
                isSupportEngineer: false,
                actualEscalationTeams: [],
                logout: jest.fn()
            })

            renderWithToast(<KnowledgeGapsPage />)

            expect(screen.queryByText('Export')).not.toBeInTheDocument()
            expect(screen.queryByText('Analysis Bundle')).not.toBeInTheDocument()
            expect(screen.queryByText('Import')).not.toBeInTheDocument()
        })

        it('hides buttons when user has LEADERSHIP role but not SUPPORT_ENGINEER', () => {
            mockUseAuth.mockReturnValue({
                user: { id: '1', email: 'test@example.com', name: 'Test User', teams: [], roles: ['LEADERSHIP'] },
                isLoading: false,
                isAuthenticated: true,
                isLeadership: true,
                isEscalationTeam: false,
                isSupportEngineer: false,
                actualEscalationTeams: [],
                logout: jest.fn()
            })

            renderWithToast(<KnowledgeGapsPage />)

            expect(screen.queryByText('Export')).not.toBeInTheDocument()
            expect(screen.queryByText('Analysis Bundle')).not.toBeInTheDocument()
            expect(screen.queryByText('Import')).not.toBeInTheDocument()
        })

        it('shows Run Analysis button when user has SUPPORT_ENGINEER role', async () => {
            mockUseAuth.mockReturnValue({
                user: { id: '1', email: 'test@example.com', name: 'Test User', teams: [], roles: ['SUPPORT_ENGINEER'] },
                isLoading: false,
                isAuthenticated: true,
                isLeadership: false,
                isEscalationTeam: false,
                isSupportEngineer: true,
                actualEscalationTeams: [],
                logout: jest.fn()
            })

            renderWithToast(<KnowledgeGapsPage />)

            expect(await screen.findByText('Run Analysis')).toBeInTheDocument()
        })
    })

    describe('Run Analysis functionality', () => {
        beforeEach(() => {
            mockUseAnalysis.mockReturnValue({
                data: mockAnalysisData,
                isLoading: false,
                error: null
            } as any)

            mockUseAuth.mockReturnValue({
                user: { id: '1', email: 'test@example.com', name: 'Test User', teams: [], roles: ['SUPPORT_ENGINEER'] },
                isLoading: false,
                isAuthenticated: true,
                isLeadership: false,
                isEscalationTeam: false,
                isSupportEngineer: true,
                actualEscalationTeams: [],
                logout: jest.fn()
            })

            // Clear all timers before each test
            jest.clearAllTimers()
            jest.useFakeTimers()
        })

        afterEach(() => {
            jest.runOnlyPendingTimers()
            jest.useRealTimers()
        })

        it('fetches analysis status on mount', async () => {
            mockApiFetch.mockImplementation((url) => {
                if (url === '/api/analysis/enabled') {
                    return Promise.resolve({
                        ok: true,
                        json: () => Promise.resolve({ enabled: true })
                    } as Response)
                }
                return Promise.resolve({
                    ok: true,
                    json: () => Promise.resolve({
                        jobId: null,
                        exportedCount: null,
                        analyzedCount: null,
                        running: false,
                        error: null
                    })
                } as Response)
            })

            renderWithToast(<KnowledgeGapsPage />)

            // Wait for the initial status fetch
            await screen.findByText('Run Analysis')

            await waitFor(() => {
                expect(mockApiFetch).toHaveBeenCalledWith('/api/analysis/status')
            })
        })

        it('starts analysis when Run Analysis button is clicked and shows progress immediately', async () => {
            let statusCallCount = 0
            mockApiFetch.mockImplementation((url, options) => {
                if (url === '/api/analysis/enabled') {
                    return Promise.resolve({
                        ok: true,
                        json: () => Promise.resolve({ enabled: true })
                    } as Response)
                }
                if (url === '/api/analysis/status') {
                    statusCallCount++
                    // First call: not running (so button is enabled)
                    // Second call (after starting): running
                    return Promise.resolve({
                        ok: true,
                        json: () => Promise.resolve({
                            jobId: statusCallCount > 1 ? 'test-job-id' : null,
                            exportedCount: statusCallCount > 1 ? 0 : null,
                            analyzedCount: statusCallCount > 1 ? 0 : null,
                            running: statusCallCount > 1,
                            error: null
                        })
                    } as Response)
                }
                if (url.startsWith('/api/analysis/run')) {
                    return Promise.resolve({
                        status: 202,
                        ok: true
                    } as Response)
                }
                return Promise.resolve({ ok: true } as Response)
            })

            renderWithToast(<KnowledgeGapsPage />)

            const startButton = await screen.findByText('Run Analysis')

            fireEvent.click(startButton)
            fireEvent.click(screen.getAllByRole('button', { name: 'Run Analysis' })[1])

            // Wait for the fetch to be called
            await waitFor(() => {
                expect(mockApiFetch).toHaveBeenCalledWith('/api/analysis/run?days=7', expect.objectContaining({
                    method: 'POST'
                }))
            })

            // Progress panel should appear
            await screen.findByText(/Checking for new threads|Analysing threads/)
            expect(screen.queryByText('Analysis settings')).not.toBeInTheDocument()
        })

        it('shows error toast when analysis start returns 409 Conflict', async () => {
            mockApiFetch.mockImplementation((url) => {
                if (url === '/api/analysis/enabled') {
                    return Promise.resolve({
                        ok: true,
                        json: () => Promise.resolve({ enabled: true })
                    } as Response)
                }
                if (url === '/api/analysis/status') {
                    return Promise.resolve({
                        ok: true,
                        json: () => Promise.resolve({
                            jobId: null,
                            exportedCount: null,
                            analyzedCount: null,
                            running: false,
                            error: null
                        })
                    } as Response)
                }
                if (url.startsWith('/api/analysis/run')) {
                    return Promise.resolve({
                        status: 409,
                        ok: false
                    } as Response)
                }
                return Promise.resolve({ ok: true } as Response)
            })

            renderWithToast(<KnowledgeGapsPage />)

            const startButton = await screen.findByText('Run Analysis')
            fireEvent.click(startButton)
            fireEvent.click(screen.getAllByRole('button', { name: 'Run Analysis' })[1])

            // Wait for error toast
            await screen.findByText('Analysis was just started by someone else')
        })

        it('disables Run Analysis button when analysis is running', async () => {
            mockApiFetch.mockImplementation((url) => {
                if (url === '/api/analysis/enabled') {
                    return Promise.resolve({
                        ok: true,
                        json: () => Promise.resolve({ enabled: true })
                    } as Response)
                }
                return Promise.resolve({
                    ok: true,
                    json: () => Promise.resolve({
                        jobId: 'test-job-id',
                        exportedCount: 10,
                        analyzedCount: 5,
                        running: true,
                        error: null
                    })
                } as Response)
            })


            renderWithToast(<KnowledgeGapsPage />)

            // Wait for progress display to appear, which indicates status has been fetched
            await screen.findByText(/Checking for new threads|Analysing threads/)

            const startButton = screen.getByText('Run Analysis')

            // Button should be disabled when analysis is running
            expect(startButton).toBeDisabled()
        })

        it('shows progress when analysis is running', async () => {
            mockApiFetch.mockImplementation((url) => {
                if (url === '/api/analysis/enabled') {
                    return Promise.resolve({
                        ok: true,
                        json: () => Promise.resolve({ enabled: true })
                    } as Response)
                }
                return Promise.resolve({
                    ok: true,
                    json: () => Promise.resolve({
                        jobId: 'test-job-id',
                        exportedCount: 10,
                        analyzedCount: 5,
                        running: true,
                        error: null
                    })
                } as Response)
            })


            renderWithToast(<KnowledgeGapsPage />)

            // Wait for progress display
            await screen.findByText(/Checking for new threads|Analysing threads/)
            expect(screen.getByText(/5 of 10 complete/)).toBeInTheDocument()
        })

        it('shows completion status in progress panel before hiding it', async () => {
            const { useQueryClient } = require('@tanstack/react-query')
            const mockInvalidateQueries = jest.fn()
            useQueryClient.mockReturnValue({
                invalidateQueries: mockInvalidateQueries
            })

            let callCount = 0
            mockApiFetch.mockImplementation((url) => {
                if (url === '/api/analysis/enabled') {
                    return Promise.resolve({
                        ok: true,
                        json: () => Promise.resolve({ enabled: true })
                    } as Response)
                }
                callCount++
                if (callCount === 1) {
                    // Initial status check - running
                    return Promise.resolve({
                        ok: true,
                        json: () => Promise.resolve({
                            jobId: 'test-job-id',
                            exportedCount: 5,
                            analyzedCount: 3,
                            running: true,
                            error: null
                        })
                    } as Response)
                } else {
                    // Subsequent check - completed
                    return Promise.resolve({
                        ok: true,
                        json: () => Promise.resolve({
                            jobId: 'test-job-id',
                            exportedCount: 10,
                            analyzedCount: 8,
                            running: false,
                            error: null
                        })
                    } as Response)
                }
            })


            renderWithToast(<KnowledgeGapsPage />)

            // Wait for initial progress display
            await screen.findByText(/Checking for new threads|Analysing threads/)

            // Fast-forward time to trigger polling
            jest.advanceTimersByTime(3000)

            // Wait for completion message
            const completionMessage = await screen.findByText(/Analysis complete! 8 of 10 threads analysed/)

            // Verify the panel shows completion status (green background gradient)
            const completionPanel = completionMessage.parentElement?.parentElement?.parentElement
            expect(completionPanel).toHaveClass('from-green-50')

            // Fast-forward another 5 seconds to hide the panel and refresh data
            jest.advanceTimersByTime(5000)

            // Verify data was refreshed
            await (() => {
                expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['analysis'] })
            })
        })
    })

    describe('Analysis Feature Flag', () => {
        it('shows Run Analysis button when feature is enabled', async () => {
            mockUseAnalysis.mockReturnValue({
                data: mockAnalysisData,
                isLoading: false,
                error: null
            } as any)

            mockApiFetch.mockImplementation((url) => {
                if (url === '/api/analysis/enabled') {
                    return Promise.resolve({
                        ok: true,
                        json: () => Promise.resolve({ enabled: true })
                    } as Response)
                }
                if (url === '/api/analysis/status') {
                    return Promise.resolve({
                        ok: true,
                        json: () => Promise.resolve({
                            jobId: null,
                            exportedCount: null,
                            analyzedCount: null,
                            running: false,
                            error: null
                        })
                    } as Response)
                }
                return Promise.resolve({ ok: true, json: () => Promise.resolve({}) } as Response)
            })



            renderWithToast(<KnowledgeGapsPage />)

            const startButton = await screen.findByText('Run Analysis')
            expect(startButton).toBeInTheDocument()
            expect(startButton).toHaveAttribute('aria-haspopup', 'dialog')
            expect(startButton).toHaveAttribute('aria-expanded', 'false')
            expect(startButton).toHaveAttribute('aria-controls', 'analysis-settings-popover')
            expect(screen.queryByText('Analysis settings')).not.toBeInTheDocument()

            fireEvent.click(startButton)

            expect(startButton).toHaveAttribute('aria-expanded', 'true')
            expect(screen.getByRole('dialog', { name: 'Analysis settings' })).toBeInTheDocument()
            const queryWindowSelect = screen.getByLabelText('Query window')
            expect(queryWindowSelect).toBeInTheDocument()
            expect(queryWindowSelect).toHaveFocus()
            expect(screen.getByText('Choose how far back to pull queries for this run.')).toBeInTheDocument()

            fireEvent.click(startButton)

            expect(startButton).toHaveAttribute('aria-expanded', 'false')
            expect(screen.queryByText('Analysis settings')).not.toBeInTheDocument()
        })

        it('keeps the settings trigger available when feature is disabled', async () => {
            mockUseAnalysis.mockReturnValue({
                data: mockAnalysisData,
                isLoading: false,
                error: null
            } as any)

            mockApiFetch.mockImplementation((url) => {
                if (url === '/api/analysis/enabled') {
                    return Promise.resolve({
                        ok: true,
                        json: () => Promise.resolve({ enabled: false })
                    } as Response)
                }
                if (url === '/api/analysis/status') {
                    return Promise.resolve({
                        ok: true,
                        json: () => Promise.resolve({
                            jobId: null,
                            exportedCount: null,
                            analyzedCount: null,
                            running: false,
                            error: null
                        })
                    } as Response)
                }
                return Promise.resolve({ ok: true, json: () => Promise.resolve({}) } as Response)
            })



            renderWithToast(<KnowledgeGapsPage />)

            // Wait for the page to render
            await screen.findByText('Support Area Summary')

            expect(screen.getByRole('button', { name: 'Run Analysis' })).toBeInTheDocument()
            expect(screen.queryByText('Analysis settings')).not.toBeInTheDocument()
        })

        it('does not fetch analysis status when feature is disabled', async () => {
            mockUseAnalysis.mockReturnValue({
                data: mockAnalysisData,
                isLoading: false,
                error: null
            } as any)

            mockApiFetch.mockImplementation((url) => {
                if (url === '/api/analysis/enabled') {
                    return Promise.resolve({
                        ok: true,
                        json: () => Promise.resolve({ enabled: false })
                    } as Response)
                }
                if (url === '/api/analysis/status') {
                    return Promise.resolve({
                        ok: true,
                        json: () => Promise.resolve({
                            jobId: 'test-job',
                            exportedCount: 5,
                            analyzedCount: 3,
                            running: true,
                            error: null
                        })
                    } as Response)
                }
                return Promise.resolve({ ok: true, json: () => Promise.resolve({}) } as Response)
            })

            renderWithToast(<KnowledgeGapsPage />)

            // Wait for the page to render
            await screen.findByText('Support Area Summary')

            // When feature is disabled, /api/analysis/status should not be called
            // This prevents 401 errors when the endpoint doesn't exist
            expect(mockApiFetch).not.toHaveBeenCalledWith('/api/analysis/status')

            // Progress panel should not be visible
            expect(screen.queryByText(/Analysing threads/)).not.toBeInTheDocument()
        })

        it('hides Export, Analysis Bundle, and Import buttons when feature is enabled', async () => {
            mockUseAnalysis.mockReturnValue({
                data: mockAnalysisData,
                isLoading: false,
                error: null
            } as any)

            mockApiFetch.mockImplementation((url) => {
                if (url === '/api/analysis/enabled') {
                    return Promise.resolve({
                        ok: true,
                        json: () => Promise.resolve({ enabled: true })
                    } as Response)
                }
                if (url === '/api/analysis/status') {
                    return Promise.resolve({
                        ok: true,
                        json: () => Promise.resolve({
                            jobId: null,
                            exportedCount: null,
                            analyzedCount: null,
                            running: false,
                            error: null
                        })
                    } as Response)
                }
                return Promise.resolve({ ok: true, json: () => Promise.resolve({}) } as Response)
            })



            renderWithToast(<KnowledgeGapsPage />)

            // Wait for the page to render and Run Analysis button to appear (proves enabled=true took effect)
            await screen.findByText('Run Analysis')

            // Verify all three buttons are not present when feature is enabled
            expect(screen.queryByText('Export')).not.toBeInTheDocument()
            expect(screen.queryByText('Analysis Bundle')).not.toBeInTheDocument()
            expect(screen.queryByText('Import')).not.toBeInTheDocument()
        })

        it('shows Export, Analysis Bundle, and Import buttons when feature is disabled', async () => {
            mockUseAnalysis.mockReturnValue({
                data: mockAnalysisData,
                isLoading: false,
                error: null
            } as any)

            mockApiFetch.mockImplementation((url) => {
                if (url === '/api/analysis/enabled') {
                    return Promise.resolve({
                        ok: true,
                        json: () => Promise.resolve({ enabled: false })
                    } as Response)
                }
                if (url === '/api/analysis/status') {
                    return Promise.resolve({
                        ok: true,
                        json: () => Promise.resolve({
                            jobId: null,
                            exportedCount: null,
                            analyzedCount: null,
                            running: false,
                            error: null
                        })
                    } as Response)
                }
                return Promise.resolve({ ok: true, json: () => Promise.resolve({}) } as Response)
            })



            renderWithToast(<KnowledgeGapsPage />)

            // Wait for the page to render
            await screen.findByText('Support Area Summary')

            const settingsTrigger = screen.getByText('Run Analysis')
            expect(settingsTrigger).toBeInTheDocument()

            fireEvent.click(settingsTrigger)

            expect(screen.getByText('Analysis settings')).toBeInTheDocument()

            const exportButton = screen.getByText('Export')
            const bundleButton = screen.getByText('Analysis Bundle')
            const importButton = screen.getByText('Import')

            expect(exportButton).toBeInTheDocument()
            expect(exportButton).not.toBeDisabled()
            expect(bundleButton).toBeInTheDocument()
            expect(bundleButton).not.toBeDisabled()
            expect(importButton).toBeInTheDocument()
            expect(importButton).not.toBeDisabled()
        })

        it('closes the settings panel on Escape and outside click', async () => {
            mockUseAnalysis.mockReturnValue({
                data: mockAnalysisData,
                isLoading: false,
                error: null
            } as any)

            renderWithToast(<KnowledgeGapsPage />)

            const settingsTrigger = await screen.findByRole('button', { name: 'Run Analysis' })
            fireEvent.click(settingsTrigger)

            const queryWindowSelect = screen.getByLabelText('Query window')

            expect(screen.getByRole('dialog', { name: 'Analysis settings' })).toBeInTheDocument()
            expect(queryWindowSelect).toHaveFocus()

            fireEvent.keyDown(document, { key: 'Escape' })
            expect(screen.queryByText('Analysis settings')).not.toBeInTheDocument()
            expect(settingsTrigger).toHaveFocus()

            fireEvent.click(settingsTrigger)
            expect(screen.getByRole('dialog', { name: 'Analysis settings' })).toBeInTheDocument()
            expect(screen.getByLabelText('Query window')).toHaveFocus()

            fireEvent.pointerDown(document.body)
            expect(screen.queryByText('Analysis settings')).not.toBeInTheDocument()
            expect(settingsTrigger).toHaveFocus()
        })
    })
})
