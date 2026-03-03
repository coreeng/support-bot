import React from 'react'
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react'
import KnowledgeGapsPage from '../knowledge-gaps'
import * as hooks from '../../../lib/hooks'
import { ToastProvider } from '@/components/ui/toast'
import * as useAuthHook from '../../../hooks/useAuth'

// Mock the hooks
jest.mock('../../../lib/hooks')
jest.mock('../../../hooks/useAuth')

// Mock next-auth
jest.mock('next-auth/react', () => ({
    getCsrfToken: jest.fn(() => Promise.resolve('mock-csrf-token'))
}))

// Mock @tanstack/react-query
jest.mock('@tanstack/react-query', () => ({
    ...jest.requireActual('@tanstack/react-query'),
    useQueryClient: jest.fn(() => ({
        invalidateQueries: jest.fn()
    }))
}))

// Helper to render with ToastProvider
const renderWithToast = (component: React.ReactElement) => {
    return render(<ToastProvider>{component}</ToastProvider>)
}

const mockUseAnalysis = hooks.useAnalysis as jest.MockedFunction<typeof hooks.useAnalysis>
const mockUseAuth = useAuthHook.useAuth as jest.MockedFunction<typeof useAuthHook.useAuth>

const mockAnalysisData = {
    knowledgeGaps: [
        {
            name: 'Connectivity and Networking',
            coveragePercentage: 90,
            queryCount: 28,
            queries: [
                { text: 'Firewall rules for output traffic', link: 'https://slack.com/archives/CTEST/p' },
                { text: 'DNS resolution issues', link: 'https://slack.com/archives/CTEST/p' }
            ]
        },
        {
            name: 'Monitoring & Troubleshooting Tenant Applications',
            coveragePercentage: 88,
            queryCount: 50,
            queries: [
                { text: 'How to view application logs?', link: 'https://slack.com/archives/CTEST/p' },
                { text: 'Setting up custom metrics', link: 'https://slack.com/archives/CTEST/p' }
            ]
        },
        {
            name: 'CI',
            coveragePercentage: 75,
            queryCount: 42,
            queries: [
                { text: 'How do I fix the CI pipeline failure?', link: 'https://slack.com/archives/CTEST/p' },
                { text: 'What is the correct configuration for the build step?', link: 'https://slack.com/archives/CTEST/p' }
            ]
        },
        {
            name: 'Configuring Platform Features - Kafka and Dial',
            coveragePercentage: 60,
            queryCount: 35,
            queries: [
                { text: 'How to setup Kafka consumers?', link: 'https://slack.com/archives/CTEST/p' },
                { text: 'Dial configuration for new tenant', link: 'https://slack.com/archives/CTEST/p' }
            ]
        },
        {
            name: 'Deploying & Configuring Tenant Applications',
            coveragePercentage: 45,
            queryCount: 15,
            queries: [
                { text: 'Deployment failed with timeout', link: 'https://slack.com/archives/CTEST/p' },
                { text: 'Configuring environment variables', link: 'https://slack.com/archives/CTEST/p' }
            ]
        }
    ],
    supportAreas: [
        {
            name: 'Knowledge Gap',
            coveragePercentage: 56,
            queryCount: 2127,
            queries: [
                { text: 'Documentation missing for new API', link: 'https://slack.com/archives/CTEST/p' },
                { text: 'How to configure advanced settings?', link: 'https://slack.com/archives/CTEST/p' }
            ]
        },
        {
            name: 'Product Temporary Issue',
            coveragePercentage: 22,
            queryCount: 825,
            queries: [
                { text: 'Service temporarily unavailable', link: 'https://slack.com/archives/CTEST/p' },
                { text: '503 errors on login', link: 'https://slack.com/archives/CTEST/p' }
            ]
        },
        {
            name: 'Task Request',
            coveragePercentage: 13,
            queryCount: 493,
            queries: [
                { text: 'Please reset my API key', link: 'https://slack.com/archives/CTEST/p' },
                { text: 'Update billing address', link: 'https://slack.com/archives/CTEST/p' }
            ]
        },
        {
            name: 'Product Usability Problem',
            coveragePercentage: 5,
            queryCount: 196,
            queries: [
                { text: 'Cannot find the logout button', link: 'https://slack.com/archives/CTEST/p' },
                { text: 'Dashboard is confusing', link: 'https://slack.com/archives/CTEST/p' }
            ]
        },
        {
            name: 'Feature Request',
            coveragePercentage: 4,
            queryCount: 163,
            queries: [
                { text: 'Add dark mode support', link: 'https://slack.com/archives/CTEST/p' },
                { text: 'Export report to PDF', link: 'https://slack.com/archives/CTEST/p' }
            ]
        }
    ]
}

describe('KnowledgeGapsPage', () => {
    beforeEach(() => {
        jest.clearAllMocks()
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

        // Default mock for fetch to handle /api/analysis/enabled
        global.fetch = jest.fn((url) => {
            if (url === '/api/analysis/enabled') {
                return Promise.resolve({
                    ok: true,
                    json: () => Promise.resolve({ enabled: true })
                } as Response)
            }
            return Promise.resolve({
                ok: true,
                json: () => Promise.resolve({})
            } as Response)
        }) as jest.Mock
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

        // Check for import, export, and prompt buttons
        expect(screen.getByText('Import')).toBeInTheDocument()
        expect(screen.getByText('Export')).toBeInTheDocument()
        expect(screen.getByText('Analysis Bundle')).toBeInTheDocument()

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
        fireEvent.click(supportAreasButton)

        // List item hidden
        expect(screen.queryByText('Knowledge Gap')).not.toBeInTheDocument()

        // Collapse knowledge gaps
        const knowledgeGapsButton = screen.getByRole('button', { name: /Top Knowledge Gaps/i })
        fireEvent.click(knowledgeGapsButton)

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
        fireEvent.click(screen.getByText('Knowledge Gap'))

        // Now queries should be visible
        expect(screen.getByText('Documentation missing for new API')).toBeInTheDocument()
        expect(screen.getByText('How to configure advanced settings?')).toBeInTheDocument()

        // Click again to collapse
        fireEvent.click(screen.getByText('Knowledge Gap'))

        // Queries should be hidden
        expect(screen.queryByText('Documentation missing for new API')).not.toBeInTheDocument()
    })

    it('query links point to Slack permalinks', () => {
        mockUseAnalysis.mockReturnValue({
            data: mockAnalysisData,
            isLoading: false,
            error: null
        } as any)

        renderWithToast(<KnowledgeGapsPage />)

        // Expand the "Knowledge Gap" support area to reveal queries
        fireEvent.click(screen.getByText('Knowledge Gap'))

        // Query links should point to Slack permalinks and open in a new tab
        const links = screen.getAllByRole('link', { name: /view/i })
        expect(links[0]).toHaveAttribute('href', 'https://slack.com/archives/CTEST/p')
        expect(links[1]).toHaveAttribute('href', 'https://slack.com/archives/CTEST/p')
        expect(links[0]).toHaveAttribute('target', '_blank')
        expect(links[0]).toHaveAttribute('rel', 'noopener noreferrer')
    })

    it('renders queries with null links as plain text without anchor tags', () => {
        const dataWithNullLinks = {
            ...mockAnalysisData,
            supportAreas: [
                {
                    name: 'Mixed Links',
                    coveragePercentage: 50,
                    queryCount: 10,
                    queries: [
                        { text: 'Query with link', link: 'https://slack.com/archives/CTEST/p123' },
                        { text: 'Query without link', link: null }
                    ]
                }
            ]
        }

        mockUseAnalysis.mockReturnValue({
            data: dataWithNullLinks,
            isLoading: false,
            error: null
        } as any)

        renderWithToast(<KnowledgeGapsPage />)

        // Expand the item to reveal queries
        fireEvent.click(screen.getByText('Mixed Links'))

        // Both query texts should be visible
        expect(screen.getByText('Query with link')).toBeInTheDocument()
        expect(screen.getByText('Query without link')).toBeInTheDocument()

        // Only the non-null query has a clickable link
        const links = screen.getAllByRole('link', { name: /view/i })
        expect(links).toHaveLength(1)
        expect(links[0]).toHaveAttribute('href', 'https://slack.com/archives/CTEST/p123')
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

        const exportButton = screen.getByText('Export')
        fireEvent.click(exportButton)

        // Wait for async operations
        await screen.findByText('Downloading...')

        // Verify fetch was called with default value of 7 days (Week)
        expect(mockFetch).toHaveBeenCalledWith('/api/summary-data/export?days=7', {
            headers: {
                'X-CSRF-Token': 'mock-csrf-token',
            },
        })

        // Wait for button to return to normal state
        await screen.findByText('Export')

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

        // Find the select dropdown - default is Week (7 days)
        const select = await screen.findByDisplayValue('Week')
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

        // Mock fetch
        const mockFetch = jest.fn((url) => {
            if (url === '/api/analysis/enabled') {
                return Promise.resolve({
                    ok: true,
                    json: () => Promise.resolve({ enabled: true })
                } as Response)
            }
            return Promise.resolve({
                ok: true,
                blob: () => Promise.resolve(new Blob(['test'], { type: 'application/zip' }))
            } as Response)
        })
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

        // Change the time period to Month (31 days)
        const select = await screen.findByDisplayValue('Week')
        fireEvent.change(select, { target: { value: '31' } })

        // Click export button
        const exportButton = screen.getByText('Export')
        fireEvent.click(exportButton)

        // Wait for the fetch to be called
        await new Promise(resolve => setTimeout(resolve, 100))

        // Verify fetch was called with days=31
        expect(mockFetch).toHaveBeenCalledWith('/api/summary-data/export?days=31', {
            headers: {
                'X-CSRF-Token': 'mock-csrf-token',
            },
        })
    })

    it('uses quarter time period when exporting data', async () => {
        mockUseAnalysis.mockReturnValue({
            data: mockAnalysisData,
            isLoading: false,
            error: null
        } as any)

        // Mock fetch
        const mockFetch = jest.fn((url) => {
            if (url === '/api/analysis/enabled') {
                return Promise.resolve({
                    ok: true,
                    json: () => Promise.resolve({ enabled: true })
                } as Response)
            }
            return Promise.resolve({
                ok: true,
                blob: () => Promise.resolve(new Blob(['test'], { type: 'application/zip' }))
            } as Response)
        })
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

        // Change the time period to Quarter (92 days)
        const select = await screen.findByDisplayValue('Week')
        fireEvent.change(select, { target: { value: '92' } })

        // Click export button
        const exportButton = screen.getByText('Export')
        fireEvent.click(exportButton)

        // Wait for the fetch to be called
        await new Promise(resolve => setTimeout(resolve, 100))

        // Verify fetch was called with days=92
        expect(mockFetch).toHaveBeenCalledWith('/api/summary-data/export?days=92', {
            headers: {
                'X-CSRF-Token': 'mock-csrf-token',
            },
        })
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

        // Mock fetch for upload
        const mockFetch = jest.fn(() =>
            Promise.resolve({
                ok: true,
                json: () => Promise.resolve({ recordsImported: 42, message: 'Import successful' })
            } as Response)
        )
        global.fetch = mockFetch

        renderWithToast(<KnowledgeGapsPage />)

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

        // Verify fetch was called with FormData
        expect(mockFetch).toHaveBeenCalledWith('/api/summary-data/import', expect.objectContaining({
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

        // Mock fetch
        const mockFetch = jest.fn(() =>
            Promise.resolve({
                ok: true,
                blob: () => Promise.resolve(new Blob(['test prompt'], { type: 'application/zip' }))
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

        const promptButton = screen.getByText('Analysis Bundle')
        fireEvent.click(promptButton)

        // Wait for async operations
        await new Promise(resolve => setTimeout(resolve, 100))

        // Verify fetch was called
        expect(mockFetch).toHaveBeenCalledWith('/api/summary-data/analysis', {
            headers: {
                'X-CSRF-Token': 'mock-csrf-token',
            },
        })

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

            expect(screen.getByText('Export')).toBeInTheDocument()
            expect(screen.getByText('Analysis Bundle')).toBeInTheDocument()
            expect(screen.getByText('Import')).toBeInTheDocument()
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

        it('shows Start Analysis button when user has SUPPORT_ENGINEER role', async () => {
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

            expect(await screen.findByText('Start Analysis')).toBeInTheDocument()
        })
    })

    describe('Start Analysis functionality', () => {
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
            const mockFetch = jest.fn((url) => {
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
            global.fetch = mockFetch

            renderWithToast(<KnowledgeGapsPage />)

            // Wait for the initial status fetch
            await screen.findByText('Start Analysis')

            expect(mockFetch).toHaveBeenCalledWith('/api/analysis/status')
        })

        it('starts analysis when Start Analysis button is clicked and shows progress immediately', async () => {
            let statusCallCount = 0
            const mockFetch = jest.fn((url, options) => {
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

            global.fetch = mockFetch

            renderWithToast(<KnowledgeGapsPage />)

            const startButton = await screen.findByText('Start Analysis')

            // Click the button
            fireEvent.click(startButton)

            // Wait for the fetch to be called
            await waitFor(() => {
                expect(mockFetch).toHaveBeenCalledWith('/api/analysis/run?days=7', expect.objectContaining({
                    method: 'POST'
                }))
            })

            // Progress panel should appear
            await screen.findByText(/Analysis in progress/)
        })

        it('shows error toast when analysis start returns 409 Conflict', async () => {
            const mockFetch = jest.fn((url) => {
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

            global.fetch = mockFetch

            renderWithToast(<KnowledgeGapsPage />)

            const startButton = await screen.findByText('Start Analysis')
            fireEvent.click(startButton)

            // Wait for error toast
            await screen.findByText('Analysis was just started by someone else')
        })

        it('disables Start Analysis button when analysis is running', async () => {
            const mockFetch = jest.fn((url) => {
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
            global.fetch = mockFetch

            renderWithToast(<KnowledgeGapsPage />)

            // Wait for progress display to appear, which indicates status has been fetched
            await screen.findByText(/Analysis in progress/)

            const startButton = screen.getByText('Start Analysis')

            // Button should be disabled when analysis is running
            expect(startButton).toBeDisabled()
        })

        it('shows progress when analysis is running', async () => {
            const mockFetch = jest.fn((url) => {
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
            global.fetch = mockFetch

            renderWithToast(<KnowledgeGapsPage />)

            // Wait for progress display
            await screen.findByText(/Analysis in progress/)
            expect(screen.getByText(/Exported: 10, Analyzed: 5/)).toBeInTheDocument()
        })

        it('shows completion status in progress panel before hiding it', async () => {
            const { useQueryClient } = require('@tanstack/react-query')
            const mockInvalidateQueries = jest.fn()
            useQueryClient.mockReturnValue({
                invalidateQueries: mockInvalidateQueries
            })

            let callCount = 0
            const mockFetch = jest.fn((url) => {
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
            global.fetch = mockFetch

            renderWithToast(<KnowledgeGapsPage />)

            // Wait for initial progress display
            await screen.findByText(/Analysis in progress/)

            // Fast-forward time to trigger polling
            jest.advanceTimersByTime(3000)

            // Wait for completion message
            const completionMessage = await screen.findByText(/Analysis complete! Exported: 10, Analyzed: 8/)

            // Verify the panel shows completion status (green background)
            // The parent div with the bg-green-50 class is 3 levels up from the text
            const completionPanel = completionMessage.parentElement?.parentElement
            expect(completionPanel).toHaveClass('bg-green-50')

            // Fast-forward another 5 seconds to hide the panel and refresh data
            jest.advanceTimersByTime(5000)

            // Verify data was refreshed
            await (() => {
                expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['analysis'] })
            })
        })
    })

    describe('Analysis Feature Flag', () => {
        it('shows Start Analysis button when feature is enabled', async () => {
            mockUseAnalysis.mockReturnValue({
                data: mockAnalysisData,
                isLoading: false,
                error: null
            } as any)

            const mockFetch = jest.fn((url) => {
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

            global.fetch = mockFetch

            renderWithToast(<KnowledgeGapsPage />)

            // Wait for the button to appear
            const startButton = await screen.findByText('Start Analysis')
            expect(startButton).toBeInTheDocument()
        })

        it('hides Start Analysis button when feature is disabled', async () => {
            mockUseAnalysis.mockReturnValue({
                data: mockAnalysisData,
                isLoading: false,
                error: null
            } as any)

            const mockFetch = jest.fn((url) => {
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

            global.fetch = mockFetch

            renderWithToast(<KnowledgeGapsPage />)

            // Wait for the page to render
            await screen.findByText('Support Area Summary')

            // Verify the button is not present
            expect(screen.queryByText('Start Analysis')).not.toBeInTheDocument()
            // Also verify the days selector is not present
            expect(screen.queryByDisplayValue('Week')).not.toBeInTheDocument()
        })

        it('hides progress panel when feature is disabled', async () => {
            mockUseAnalysis.mockReturnValue({
                data: mockAnalysisData,
                isLoading: false,
                error: null
            } as any)

            const mockFetch = jest.fn((url) => {
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

            global.fetch = mockFetch

            renderWithToast(<KnowledgeGapsPage />)

            // Wait for the page to render
            await screen.findByText('Support Area Summary')

            // Verify the progress panel is not shown even though analysis is running
            expect(screen.queryByText(/Analysis in progress/)).not.toBeInTheDocument()
        })
    })
})
