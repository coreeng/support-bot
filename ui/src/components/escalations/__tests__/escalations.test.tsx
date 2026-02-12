import React from 'react'
import { render, screen } from '@testing-library/react'
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
                        openedAt: '2025-01-01T00:00:00Z',
                        resolvedAt: null,
                        threadLink: null,
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
                        openedAt: '2025-01-01T00:00:00Z',
                        resolvedAt: null,
                        threadLink: null,
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
                        openedAt: '2025-01-01T10:00:00Z',
                        resolvedAt: null,
                        threadLink: null,
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
                        threadLink: null,
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
                        threadLink: null,
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


