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

// Mock useUrlParams with a useState-based implementation so filter/sort/page
// changes re-render the component correctly, keeping all existing test
// interactions that fire events and then inspect the rendered output intact.
jest.mock('../../../lib/hooks/useUrlParams', () => ({
    ...jest.requireActual('../../../lib/hooks/useUrlParams'),
    useUrlParams: (defaults: Record<string, string>) => {
        const { useState } = require('react') as typeof import('react')
        const [params, setParamsState] = useState<Record<string, string>>(defaults)
        const setParams = (updates: Record<string, string>) => {
            setParamsState((prev: Record<string, string>) => ({ ...prev, ...updates }))
        }
        return [params, setParams]
    },
}))
jest.mock('../EscalatedToMyTeamTable', () => {
    const Mock = () => <div data-testid="escalated-to-my-team-table" />
    Mock.displayName = 'MockEscalatedToMyTeamTable'
    return Mock
})

const mockUseEscalations = hooks.useEscalations as jest.MockedFunction<typeof hooks.useEscalations>
const mockUseTenantTeams = hooks.useTenantTeams as jest.MockedFunction<typeof hooks.useTenantTeams>
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

        mockUseTenantTeams.mockReturnValue({
            data: [{ name: 'Tenant Alpha' }],
            isLoading: false,
            error: null,
        } as unknown as ReturnType<typeof hooks.useTenantTeams>)

        mockUseTeamFilter.mockReturnValue({
            selectedTeam: 'Escalation Team 2 Test',
            setSelectedTeam: jest.fn(),
            teamScope: { mode: 'selected_teams', teams: ['Escalation Team 2 Test'] },
            effectiveTeams: ['Escalation Team 2 Test'],
            hasNoTeamScope: false,
            isViewingAllTeams: false,
            isViewingAsEscalationTeam: true,
            hasFullAccess: false,
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

        expect(screen.getByText(/Escalated for Escalation Team 2 Test/i)).toBeInTheDocument()
        expect(screen.queryByText('T-1')).not.toBeInTheDocument()
        expect(screen.getByText(/No escalations found/i)).toBeInTheDocument()
    })

    it('always shows "Escalated To" column even without full access', () => {
        mockUseTeamFilter.mockReturnValue({
            selectedTeam: 'Escalation Team 2 Test',
            setSelectedTeam: jest.fn(),
            teamScope: { mode: 'selected_teams', teams: ['Escalation Team 2 Test'] },
            effectiveTeams: ['Escalation Team 2 Test'],
            hasNoTeamScope: false,
            isViewingAllTeams: false,
            isViewingAsEscalationTeam: true,
            hasFullAccess: false,
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

        expect(screen.getByText('Escalated To')).toBeInTheDocument()
        expect(screen.queryByText('Escalated For')).not.toBeInTheDocument()
    })

    it('shows "Escalated For" in leadership/support full-access view', () => {
        mockUseTeamFilter.mockReturnValue({
            selectedTeam: null,
            setSelectedTeam: jest.fn(),
            teamScope: { mode: 'all_teams' },
            effectiveTeams: [],
            hasNoTeamScope: false,
            isViewingAllTeams: true,
            isViewingAsEscalationTeam: false,
            hasFullAccess: true,
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
                        ticketId: 'T-501',
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

        expect(screen.getByText('Escalated For')).toBeInTheDocument()
        expect(screen.getByText('Escalated To')).toBeInTheDocument()
        expect(screen.getAllByText('Tenant Alpha').length).toBeGreaterThan(0)
        expect(screen.getByText('Escalation Team 1')).toBeInTheDocument()
    })

    it('hides team scope controls and context labels when user has no backend team scope', () => {
        mockUseTeamFilter.mockReturnValue({
            selectedTeam: null,
            setSelectedTeam: jest.fn(),
            teamScope: { mode: 'no_teams' },
            effectiveTeams: ['__no_teams__'],
            hasNoTeamScope: true,
            isViewingAllTeams: false,
            isViewingAsEscalationTeam: false,
            hasFullAccess: false,
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