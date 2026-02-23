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
const mockUseTenantTeams = hooks.useTenantTeams as jest.MockedFunction<typeof hooks.useTenantTeams>
const mockUseRegistry = hooks.useRegistry as jest.MockedFunction<typeof hooks.useRegistry>
const mockUseTeamFilter = TeamFilterContext.useTeamFilter as jest.MockedFunction<typeof TeamFilterContext.useTeamFilter>
const mockUseAuth = AuthHook.useAuth as jest.MockedFunction<typeof AuthHook.useAuth>

const Wrapper = ({ children }: { children: React.ReactNode }) => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
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

        mockUseTenantTeams.mockReturnValue({
            data: [{ name: 'Tenant Alpha' }],
            isLoading: false,
            error: null,
        } as unknown as ReturnType<typeof hooks.useTenantTeams>)

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
                        openedAt: '2025-01-01T00:00:00Z',
                        resolvedAt: null,
                        hasThread: false,
                    },
                ],
            },
            isLoading: false,
            error: null,
        } as unknown as ReturnType<typeof hooks.useEscalations>)

        render(<EscalationsPage />, { wrapper: Wrapper })

        expect(screen.getByText(/Escalated for Escalation Team 2 Test/i)).toBeInTheDocument()
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
                        openedAt: '2025-01-01T00:00:00Z',
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
        expect(screen.getAllByText('Tenant Alpha').length).toBeGreaterThan(0)
        expect(screen.getByText('Escalation Team 1')).toBeInTheDocument()
    })

    it('deduplicates escalations by ticketId in escalation team scoped view', () => {
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
                        openedAt: '2025-01-01T10:00:00Z',
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
                        openedAt: '2025-01-01T11:00:00Z', // More recent
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
                        openedAt: '2025-01-02T10:00:00Z',
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

    it('resets page-level team filter to current scope when sidebar team changes', () => {
        mockUseAuth.mockReturnValue({
            user: null,
            isLoading: false,
            isAuthenticated: true,
            isLeadership: false,
            isEscalationTeam: true,
            isSupportEngineer: false,
            actualEscalationTeams: ['Escalation Team 2 Test', 'Escalation Team 3 Test'],
            logout: jest.fn()
        })

        mockUseEscalations.mockReturnValue({
            data: {
                page: 0,
                totalPages: 1,
                totalElements: 2,
                content: [
                    {
                        id: 'esc-1',
                        ticketId: 'T-101',
                        escalatingTeam: 'Tenant Alpha',
                        team: { name: 'Escalation Team 2 Test' },
                        impact: 'high',
                        tags: ['api'],
                        openedAt: '2025-01-01T00:00:00Z',
                        resolvedAt: null,
                        hasThread: false,
                    },
                    {
                        id: 'esc-2',
                        ticketId: 'T-102',
                        escalatingTeam: 'Tenant Beta',
                        team: { name: 'Escalation Team 2 Test' },
                        impact: 'high',
                        tags: ['db'],
                        openedAt: '2025-01-02T00:00:00Z',
                        resolvedAt: null,
                        hasThread: false,
                    },
                ],
            },
            isLoading: false,
            error: null,
        } as unknown as ReturnType<typeof hooks.useEscalations>)

        mockUseTenantTeams.mockReturnValue({
            data: [{ name: 'Tenant Alpha' }, { name: 'Tenant Beta' }],
            isLoading: false,
            error: null,
        } as unknown as ReturnType<typeof hooks.useTenantTeams>)

        mockUseTeamFilter.mockReturnValue({
            selectedTeam: 'Escalation Team 2 Test',
            setSelectedTeam: jest.fn(),
            hasFullAccess: false,
            effectiveTeams: ['Escalation Team 2 Test'],
            allTeams: ['Escalation Team 2 Test', 'Escalation Team 3 Test'],
            initialized: true
        })

        const { rerender } = render(<EscalationsPage />, { wrapper: Wrapper })

        const tenantTeamSelect = screen.getByTestId('escalations-team-filter') as HTMLSelectElement
        fireEvent.change(tenantTeamSelect, { target: { value: 'Tenant Beta' } })
        expect(tenantTeamSelect.value).toBe('Tenant Beta')
        expect(screen.getByText('Escalated for Tenant Beta')).toBeInTheDocument()

        mockUseTeamFilter.mockReturnValue({
            selectedTeam: 'Escalation Team 3 Test',
            setSelectedTeam: jest.fn(),
            hasFullAccess: false,
            effectiveTeams: ['Escalation Team 3 Test'],
            allTeams: ['Escalation Team 2 Test', 'Escalation Team 3 Test'],
            initialized: true
        })

        rerender(<EscalationsPage />)

        const resetTeamSelect = screen.getByTestId('escalations-team-filter') as HTMLSelectElement
        expect(resetTeamSelect.value).toBe('')
    })

    it('filters by escalating tenant team (not target escalation team)', () => {
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

        mockUseTenantTeams.mockReturnValue({
            data: [{ name: 'Tenant Alpha' }, { name: 'Tenant Beta' }],
            isLoading: false,
            error: null,
        } as unknown as ReturnType<typeof hooks.useTenantTeams>)

        mockUseEscalations.mockReturnValue({
            data: {
                page: 0,
                totalPages: 1,
                totalElements: 2,
                content: [
                    {
                        id: 'esc-1',
                        ticketId: 'T-201',
                        escalatingTeam: 'Tenant Alpha',
                        team: { name: 'Shared Target Team' },
                        impact: 'high',
                        tags: ['api'],
                        openedAt: '2025-01-01T00:00:00Z',
                        resolvedAt: null,
                        hasThread: false,
                    },
                    {
                        id: 'esc-2',
                        ticketId: 'T-202',
                        escalatingTeam: 'Tenant Beta',
                        team: { name: 'Shared Target Team' },
                        impact: 'high',
                        tags: ['db'],
                        openedAt: '2025-01-02T00:00:00Z',
                        resolvedAt: null,
                        hasThread: false,
                    },
                ],
            },
            isLoading: false,
            error: null,
        } as unknown as ReturnType<typeof hooks.useEscalations>)

        render(<EscalationsPage />, { wrapper: Wrapper })

        const tenantTeamSelect = screen.getByTestId('escalations-team-filter')
        fireEvent.change(tenantTeamSelect, { target: { value: 'Tenant Beta' } })

        expect(screen.getByText('T-202')).toBeInTheDocument()
        expect(screen.queryByText('T-201')).not.toBeInTheDocument()
    })

    it('updates second table and top-tags titles for current, all, and specific team filters', () => {
        mockUseEscalations.mockReturnValue({
            data: {
                page: 0,
                totalPages: 1,
                totalElements: 2,
                content: [
                    {
                        id: 'esc-1',
                        ticketId: 'T-301',
                        escalatingTeam: 'Tenant Alpha',
                        team: { name: 'Escalation Team 2 Test' },
                        impact: 'high',
                        tags: ['api'],
                        openedAt: '2025-01-01T00:00:00Z',
                        resolvedAt: null,
                        hasThread: false,
                    },
                    {
                        id: 'esc-2',
                        ticketId: 'T-302',
                        escalatingTeam: 'Tenant Beta',
                        team: { name: 'Escalation Team 2 Test' },
                        impact: 'high',
                        tags: ['db'],
                        openedAt: '2025-01-02T00:00:00Z',
                        resolvedAt: null,
                        hasThread: false,
                    },
                ],
            },
            isLoading: false,
            error: null,
        } as unknown as ReturnType<typeof hooks.useEscalations>)

        mockUseTenantTeams.mockReturnValue({
            data: [{ name: 'Tenant Alpha' }, { name: 'Tenant Beta' }],
            isLoading: false,
            error: null,
        } as unknown as ReturnType<typeof hooks.useTenantTeams>)

        render(<EscalationsPage />, { wrapper: Wrapper })

        // Current scope (empty page-level selection)
        expect(screen.getByText('Escalated for Escalation Team 2 Test')).toBeInTheDocument()
        expect(screen.getByText(/^Top 2 Tags\b/)).toBeInTheDocument()

        const tenantTeamSelect = screen.getByTestId('escalations-team-filter')

        // All teams explicit override
        fireEvent.change(tenantTeamSelect, { target: { value: '__all__' } })
        expect(screen.getByText('All Escalations')).toBeInTheDocument()
        expect(screen.getByText('Top 2 Tags for All Teams')).toBeInTheDocument()

        // Specific team explicit override
        fireEvent.change(tenantTeamSelect, { target: { value: 'Tenant Alpha' } })
        expect(screen.getByText('Escalated for Tenant Alpha')).toBeInTheDocument()
        expect(screen.getByText('Top 2 Tags for Tenant Alpha')).toBeInTheDocument()
    })

    it('hides team scope controls and context labels when user has no backend team scope', () => {
        mockUseTeamFilter.mockReturnValue({
            selectedTeam: null,
            setSelectedTeam: jest.fn(),
            hasFullAccess: false,
            effectiveTeams: ['__no_teams__'],
            allTeams: [],
            initialized: true
        })

        mockUseEscalations.mockReturnValue({
            data: {
                page: 0,
                totalPages: 1,
                totalElements: 1,
                content: [
                    {
                        id: 'esc-1',
                        ticketId: 'T-999',
                        escalatingTeam: 'Tenant Alpha',
                        team: { name: 'Escalation Team 2 Test' },
                        impact: 'high',
                        tags: ['api'],
                        openedAt: '2025-01-01T00:00:00Z',
                        resolvedAt: null,
                        hasThread: false,
                    },
                ],
            },
            isLoading: false,
            error: null,
        } as unknown as ReturnType<typeof hooks.useEscalations>)

        render(<EscalationsPage />, { wrapper: Wrapper })

        expect(screen.queryByTestId('escalations-team-filter')).not.toBeInTheDocument()
        expect(screen.queryByText(/Scope:/i)).not.toBeInTheDocument()
    })
})


