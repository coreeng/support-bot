import React from 'react'
import {fireEvent, render, screen, waitFor} from '@testing-library/react'
import {Toaster} from 'sonner'
import KnowledgeGapsPage from '../knowledge-gaps'
import * as hooks from '../../../lib/hooks'
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

const renderWithToast = (component: React.ReactElement) => {
    return render(<>{component}<Toaster /></>)
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
                { text: 'Firewall rules for output traffic', timestamp: '2025-03-31T13:15:00Z', ticketId: '101' },
                { text: 'DNS resolution issues', timestamp: '2025-03-30T14:20:00Z', ticketId: '102' }
            ]
        },
        {
            name: 'Monitoring & Troubleshooting Tenant Applications',
            coveragePercentage: 88,
            queryCount: 50,
            queries: [
                { text: 'How to view application logs?', timestamp: '2025-03-29T10:00:00Z', ticketId: '103' },
                { text: 'Setting up custom metrics', timestamp: '2025-03-28T06:05:00Z', ticketId: '104' }
            ]
        },
        {
            name: 'CI',
            coveragePercentage: 75,
            queryCount: 42,
            queries: [
                { text: 'How do I fix the CI pipeline failure?', timestamp: '2025-03-27T11:30:00Z', ticketId: '105' },
                { text: 'What is the correct configuration for the build step?', timestamp: '2025-03-26T16:10:00Z', ticketId: '106' }
            ]
        },
        {
            name: 'Configuring Platform Features - Kafka and Dial',
            coveragePercentage: 60,
            queryCount: 35,
            queries: [
                { text: 'How to setup Kafka consumers?', timestamp: '2025-03-25T16:10:00Z', ticketId: '107' },
                { text: 'Dial configuration for new tenant', timestamp: '2025-03-24T17:40:00Z', ticketId: '108' }
            ]
        },
        {
            name: 'Deploying & Configuring Tenant Applications',
            coveragePercentage: 45,
            queryCount: 15,
            queries: [
                { text: 'Deployment failed with timeout', timestamp: '2025-03-23T12:25:00Z', ticketId: '109' },
                { text: 'Configuring environment variables', timestamp: '2025-03-22T21:50:00Z', ticketId: '110' }
            ]
        }
    ],
    supportAreas: [
        {
            name: 'Knowledge Gap',
            coveragePercentage: 56,
            queryCount: 2127,
            queries: [
                { text: 'Documentation missing for new API', timestamp: '2025-03-31T12:34:56Z', ticketId: '201' },
                { text: 'How to configure advanced settings?', timestamp: '2025-03-30T07:08:09Z', ticketId: '210' }
            ]
        },
        {
            name: 'Product Temporary Issue',
            coveragePercentage: 22,
            queryCount: 825,
            queries: [
                { text: 'Service temporarily unavailable', timestamp: '2025-03-29T06:00:00Z', ticketId: '202' },
                { text: '503 errors on login', timestamp: '2025-03-28T18:15:00Z', ticketId: '203' }
            ]
        },
        {
            name: 'Task Request',
            coveragePercentage: 13,
            queryCount: 493,
            queries: [
                { text: 'Please reset my API key', timestamp: '2025-03-27T15:00:00Z', ticketId: '204' },
                { text: 'Update billing address', timestamp: '2025-03-26T08:30:00Z', ticketId: '205' }
            ]
        },
        {
            name: 'Product Usability Problem',
            coveragePercentage: 5,
            queryCount: 196,
            queries: [
                { text: 'Cannot find the logout button', timestamp: '2025-03-25T11:11:00Z', ticketId: '206' },
                { text: 'Dashboard is confusing', timestamp: '2025-03-24T14:22:00Z', ticketId: '207' }
            ]
        },
        {
            name: 'Feature Request',
            coveragePercentage: 4,
            queryCount: 163,
            queries: [
                { text: 'Add dark mode support', timestamp: '2025-03-23T19:45:00Z', ticketId: '208' },
                { text: 'Export report to PDF', timestamp: '2025-03-22T21:10:00Z', ticketId: '209' }
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
        expect(screen.getByText('2,127 queries')).toBeInTheDocument()
        expect(screen.getByText('CI')).toBeInTheDocument()
        expect(screen.getByText('42 queries')).toBeInTheDocument()

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
        expect(screen.getByText('Up to 5 most recent queries')).toBeInTheDocument()
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
                        { text: 'Support area query', timestamp: '2025-03-31T12:34:56Z', ticketId: '401' }
                    ]
                }
            ],
            knowledgeGaps: [
                {
                    name: 'Shared Topic',
                    coveragePercentage: 44,
                    queryCount: 8,
                    queries: [
                        { text: 'Knowledge gap query', timestamp: '2025-03-30T07:08:09Z', ticketId: '402' }
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
        fireEvent.click(screen.getByRole('button', { name: /view ticket 201/i }))

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
        fireEvent.click(screen.getByRole('button', { name: /view ticket 201/i }))

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
                        { text: 'Query with ticket', timestamp: '2025-03-31T12:00:00Z', ticketId: '301' },
                        { text: 'Another query', timestamp: '2025-03-31T12:01:00Z', ticketId: '302' }
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

        expect(screen.getByRole('button', { name: /view ticket 301/i })).toBeInTheDocument()
        expect(screen.getByRole('button', { name: /view ticket 302/i })).toBeInTheDocument()
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
                        { text: 'Query with bad timestamp', timestamp: 'not-a-date', ticketId: '999' }
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

    })

    describe('Analysis Feature Flag', () => {
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

    })
})
