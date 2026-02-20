import React from 'react'
import { render, screen, fireEvent } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import EscalationsPage from '../escalations'
import * as hooks from '../../../lib/hooks'
import * as TeamFilterContext from '../../../contexts/TeamFilterContext'
import * as AuthHook from '../../../hooks/useAuth'

jest.mock('../../../lib/hooks')
jest.mock('../../../contexts/TeamFilterContext')
jest.mock('../../../hooks/useAuth')
jest.mock('../EscalatedToMyTeamTable', () => {
    const Mock = () => <div data-testid="escalated-to-my-team-table" />
    Mock.displayName = 'MockEscalatedToMyTeamTable'
    return Mock
})

const mockUseEscalations = hooks.useEscalations as jest.MockedFunction<typeof hooks.useEscalations>
const mockUseEscalationTeams = hooks.useEscalationTeams as jest.MockedFunction<typeof hooks.useEscalationTeams>
const mockUseRegistry = hooks.useRegistry as jest.MockedFunction<typeof hooks.useRegistry>
const mockUseTeamFilter = TeamFilterContext.useTeamFilter as jest.MockedFunction<typeof TeamFilterContext.useTeamFilter>
const mockUseAuth = AuthHook.useAuth as jest.MockedFunction<typeof AuthHook.useAuth>

const Wrapper = ({ children }: { children: React.ReactNode }) => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
}

/** Returns an ISO string N days before now — used to keep test data within the default "Last 7 days" filter. */
const daysAgo = (n: number) => {
    const d = new Date()
    d.setDate(d.getDate() - n)
    return d.toISOString()
}

describe('EscalationsPage', () => {
    beforeEach(() => {
        jest.clearAllMocks()

        mockUseRegistry.mockReturnValue({
            data: { impacts: [], tags: [] },
            isLoading: false,
            error: null,
        } as unknown as ReturnType<typeof hooks.useRegistry>)

        mockUseEscalationTeams.mockReturnValue({
            data: [{ name: 'Escalation Team 2 Test' }],
            isLoading: false,
            error: null,
        } as unknown as ReturnType<typeof hooks.useEscalationTeams>)

        mockUseTeamFilter.mockReturnValue({
            selectedTeam: 'Escalation Team 2 Test',
            setSelectedTeam: jest.fn(),
            hasFullAccess: false,
            effectiveTeams: ['Escalation Team 2 Test'],
            allTeams: ['Escalation Team 2 Test'],
            initialized: true
        })

        mockUseAuth.mockReturnValue({
            user: null,
            isLoading: false,
            isAuthenticated: true,
            isLeadership: false,
            isEscalationTeam: true,
            isSupportEngineer: false,
            actualEscalationTeams: ['Escalation Team 2 Test'],
            logout: jest.fn()
        })
    })

    it('does not mirror escalations targeted to my team into "Escalated for My Team"', () => {
        mockUseEscalations.mockReturnValue({
            data: {
                page: 0,
                totalPages: 1,
                totalElements: 1,
                content: [
                    {
                        id: 'esc-1',
                        ticketId: 'T-1',
                        escalatingTeam: 'Tenant Alpha',
                        team: { name: 'Escalation Team 2 Test' },
                        impact: 'high',
                        tags: [],
                        openedAt: daysAgo(1),
                        resolvedAt: null,
                        hasThread: false,
                    },
                ],
            },
            isLoading: false,
            error: null,
        } as unknown as ReturnType<typeof hooks.useEscalations>)

        render(<EscalationsPage />, { wrapper: Wrapper })

        expect(screen.getByText(/Escalated for My Team/i)).toBeInTheDocument()
        expect(screen.queryByText('T-1')).not.toBeInTheDocument()
        expect(screen.getByText(/No escalations found/i)).toBeInTheDocument()
    })

    it('shows both escalating team and target team columns in leadership view', () => {
        mockUseTeamFilter.mockReturnValue({
            selectedTeam: null,
            setSelectedTeam: jest.fn(),
            hasFullAccess: true,
            effectiveTeams: [],
            allTeams: [],
            initialized: true
        })

        mockUseAuth.mockReturnValue({
            user: null,
            isLoading: false,
            isAuthenticated: true,
            isLeadership: true,
            isEscalationTeam: false,
            isSupportEngineer: false,
            actualEscalationTeams: [],
            logout: jest.fn()
        })

        mockUseEscalations.mockReturnValue({
            data: {
                page: 0,
                totalPages: 1,
                totalElements: 1,
                content: [
                    {
                        id: 'esc-1',
                        ticketId: 'T-123',
                        escalatingTeam: 'Tenant Alpha',
                        team: { name: 'Escalation Team 1' },
                        impact: 'high',
                        tags: [],
                        openedAt: daysAgo(1),
                        resolvedAt: null,
                        hasThread: false,
                    },
                ],
            },
            isLoading: false,
            error: null,
        } as unknown as ReturnType<typeof hooks.useEscalations>)

        render(<EscalationsPage />, { wrapper: Wrapper })

        expect(screen.getByText('Escalating Team')).toBeInTheDocument()
        expect(screen.getByText('Escalated To')).toBeInTheDocument()
        expect(screen.getByText('Tenant Alpha')).toBeInTheDocument()
        expect(screen.getByText('Escalation Team 1')).toBeInTheDocument()
    })

    describe('Tag filter', () => {
        beforeEach(() => {
            mockUseTeamFilter.mockReturnValue({
                selectedTeam: null,
                setSelectedTeam: jest.fn(),
                hasFullAccess: true,
                effectiveTeams: [],
                allTeams: [],
                initialized: true
            })
            mockUseRegistry.mockReturnValue({
                data: {
                    impacts: [],
                    tags: [
                        { code: 'networking', label: 'Networking' },
                        { code: 'storage', label: 'Storage' },
                    ]
                },
                isLoading: false,
                error: null,
            } as unknown as ReturnType<typeof hooks.useRegistry>)
            mockUseEscalations.mockReturnValue({
                data: {
                    page: 0, totalPages: 1, totalElements: 2,
                    content: [
                        { id: 'e1', ticketId: 'T-1', escalatingTeam: 'Team A', team: { name: 'Infra' }, impact: null, tags: ['networking'], openedAt: daysAgo(1), resolvedAt: null, hasThread: false },
                        { id: 'e2', ticketId: 'T-2', escalatingTeam: 'Team B', team: { name: 'Infra' }, impact: null, tags: ['storage'], openedAt: daysAgo(2), resolvedAt: null, hasThread: false },
                    ],
                },
                isLoading: false, error: null,
            } as unknown as ReturnType<typeof hooks.useEscalations>)
        })

        it('renders tag options from registry', () => {
            render(<EscalationsPage />, { wrapper: Wrapper })
            const select = screen.getByTestId('escalations-tag-filter')
            expect(select).toBeInTheDocument()
            expect(screen.getByRole('option', { name: 'Networking' })).toBeInTheDocument()
            expect(screen.getByRole('option', { name: 'Storage' })).toBeInTheDocument()
        })

        it('shows all escalations when no tag is selected', () => {
            render(<EscalationsPage />, { wrapper: Wrapper })
            expect(screen.getByText('T-1')).toBeInTheDocument()
            expect(screen.getByText('T-2')).toBeInTheDocument()
        })

        it('filters escalations to only those with the selected tag', () => {
            render(<EscalationsPage />, { wrapper: Wrapper })
            fireEvent.change(screen.getByTestId('escalations-tag-filter'), { target: { value: 'networking' } })
            expect(screen.getByText('T-1')).toBeInTheDocument()
            expect(screen.queryByText('T-2')).not.toBeInTheDocument()
        })

        it('shows no escalations when selected tag matches none', () => {
            render(<EscalationsPage />, { wrapper: Wrapper })
            fireEvent.change(screen.getByTestId('escalations-tag-filter'), { target: { value: 'storage' } })
            expect(screen.queryByText('T-1')).not.toBeInTheDocument()
            expect(screen.getByText('T-2')).toBeInTheDocument()
        })

        it('resets to all escalations when tag filter is cleared', () => {
            render(<EscalationsPage />, { wrapper: Wrapper })
            fireEvent.change(screen.getByTestId('escalations-tag-filter'), { target: { value: 'networking' } })
            fireEvent.change(screen.getByTestId('escalations-tag-filter'), { target: { value: '' } })
            expect(screen.getByText('T-1')).toBeInTheDocument()
            expect(screen.getByText('T-2')).toBeInTheDocument()
        })
    })

    describe('Date filter', () => {
        beforeEach(() => {
            mockUseTeamFilter.mockReturnValue({
                selectedTeam: null,
                setSelectedTeam: jest.fn(),
                hasFullAccess: true,
                effectiveTeams: [],
                allTeams: [],
                initialized: true
            })
            const recent = new Date()
            recent.setDate(recent.getDate() - 2)
            const old = new Date()
            old.setFullYear(old.getFullYear() - 1)
            mockUseEscalations.mockReturnValue({
                data: {
                    page: 0, totalPages: 1, totalElements: 2,
                    content: [
                        { id: 'e1', ticketId: 'T-recent', escalatingTeam: 'Team A', team: { name: 'Infra' }, impact: null, tags: [], openedAt: recent.toISOString(), resolvedAt: null, hasThread: false },
                        { id: 'e2', ticketId: 'T-old', escalatingTeam: 'Team B', team: { name: 'Infra' }, impact: null, tags: [], openedAt: old.toISOString(), resolvedAt: null, hasThread: false },
                    ],
                },
                isLoading: false, error: null,
            } as unknown as ReturnType<typeof hooks.useEscalations>)
        })

        it('renders the date filter dropdown', () => {
            render(<EscalationsPage />, { wrapper: Wrapper })
            expect(screen.getByTestId('escalations-date-filter')).toBeInTheDocument()
        })

        it('defaults to last 7 days, showing only recent escalations', () => {
            render(<EscalationsPage />, { wrapper: Wrapper })
            expect(screen.getByText('T-recent')).toBeInTheDocument()
            expect(screen.queryByText('T-old')).not.toBeInTheDocument()
        })

        it('shows all escalations when date filter is cleared', () => {
            render(<EscalationsPage />, { wrapper: Wrapper })
            fireEvent.change(screen.getByTestId('escalations-date-filter'), { target: { value: '' } })
            expect(screen.getByText('T-recent')).toBeInTheDocument()
            expect(screen.getByText('T-old')).toBeInTheDocument()
        })

        it('shows custom date inputs when custom range is selected', () => {
            render(<EscalationsPage />, { wrapper: Wrapper })
            fireEvent.change(screen.getByTestId('escalations-date-filter'), { target: { value: 'custom' } })
            expect(screen.getByLabelText('Date filter start')).toBeInTheDocument()
            expect(screen.getByLabelText('Date filter end')).toBeInTheDocument()
        })

        it('filters escalations by custom date range', () => {
            render(<EscalationsPage />, { wrapper: Wrapper })
            fireEvent.change(screen.getByTestId('escalations-date-filter'), { target: { value: 'custom' } })
            const startInput = screen.getByLabelText('Date filter start')
            const endInput = screen.getByLabelText('Date filter end')
            const tenDaysAgo = new Date()
            tenDaysAgo.setDate(tenDaysAgo.getDate() - 10)
            const yesterday = new Date()
            yesterday.setDate(yesterday.getDate() - 1)
            fireEvent.change(startInput, { target: { value: tenDaysAgo.toISOString().split('T')[0] } })
            fireEvent.change(endInput, { target: { value: yesterday.toISOString().split('T')[0] } })
            expect(screen.getByText('T-recent')).toBeInTheDocument()
            expect(screen.queryByText('T-old')).not.toBeInTheDocument()
        })
    })

    it('deduplicates escalations by ticketId in "Escalated for My Team" view', () => {
        // Setup as escalation team viewing their own escalations
        mockUseTeamFilter.mockReturnValue({
            selectedTeam: 'Escalation Team 2 Test',
            setSelectedTeam: jest.fn(),
            hasFullAccess: false,
            effectiveTeams: ['Escalation Team 2 Test'],
            allTeams: ['Escalation Team 2 Test'],
            initialized: true
        })

        mockUseAuth.mockReturnValue({
            user: null,
            isLoading: false,
            isAuthenticated: true,
            isLeadership: false,
            isEscalationTeam: true,
            isSupportEngineer: false,
            actualEscalationTeams: ['Escalation Team 2 Test'],
            logout: jest.fn()
        })

        // Same ticket escalated twice
        mockUseEscalations.mockReturnValue({
            data: {
                page: 0,
                totalPages: 1,
                totalElements: 4,
                content: [
                    {
                        id: 'esc-1',
                        ticketId: 'T-100',
                        escalatingTeam: 'Escalation Team 2 Test',
                        team: { name: 'Infra Team' },
                        impact: 'high',
                        tags: [],
                        openedAt: daysAgo(2),
                        resolvedAt: null,
                        hasThread: false,
                    },
                    {
                        id: 'esc-2',
                        ticketId: 'T-100',
                        escalatingTeam: 'Escalation Team 2 Test',
                        team: { name: 'Security Team' },
                        impact: 'high',
                        tags: [],
                        openedAt: daysAgo(1), // More recent
                        resolvedAt: null,
                        hasThread: false,
                    },
                    {
                        id: 'esc-3',
                        ticketId: 'T-200',
                        escalatingTeam: 'Escalation Team 2 Test',
                        team: { name: 'Infra Team' },
                        impact: 'medium',
                        tags: [],
                        openedAt: daysAgo(3),
                        resolvedAt: null,
                        hasThread: false,
                    },
                ],
            },
            isLoading: false,
            error: null,
        } as unknown as ReturnType<typeof hooks.useEscalations>)

        render(<EscalationsPage />, { wrapper: Wrapper })

        // Should show "2 total" (unique tickets: T-100 and T-200), not 3
        expect(screen.getByText('2 total')).toBeInTheDocument()
        
        // Should show T-100 once (most recent escalation kept)
        const t100Cells = screen.getAllByText('T-100')
        expect(t100Cells).toHaveLength(1)
        
        // Should show T-200 once
        expect(screen.getByText('T-200')).toBeInTheDocument()
    })
})


