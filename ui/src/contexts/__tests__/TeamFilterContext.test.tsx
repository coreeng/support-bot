import React from 'react'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { TeamFilterProvider, useTeamFilter } from '../TeamFilterContext'
import { useAuth } from '../../hooks/useAuth'
import { TEAM_SCOPE } from '../../lib/constants'

jest.mock('../../hooks/useAuth')

const mockUseAuth = useAuth as jest.MockedFunction<typeof useAuth>

const Probe = () => {
    const { selectedTeam, setSelectedTeam, effectiveTeams, hasFullAccess, allTeams, initialized } = useTeamFilter()
    return (
        <div>
            <div data-testid="selected-team">{selectedTeam ?? 'null'}</div>
            <div data-testid="effective-teams">{effectiveTeams.join('|')}</div>
            <div data-testid="has-full-access">{String(hasFullAccess)}</div>
            <div data-testid="all-teams">{allTeams.join('|')}</div>
            <div data-testid="initialized">{String(initialized)}</div>
            <button onClick={() => setSelectedTeam('Tenant B')}>select-tenant-b</button>
            <button onClick={() => setSelectedTeam(null)}>select-null</button>
        </div>
    )
}

const renderProvider = () =>
    render(
        <TeamFilterProvider>
            <Probe />
        </TeamFilterProvider>
    )

describe('TeamFilterContext', () => {
    beforeEach(() => {
        jest.clearAllMocks()
    })

    it('returns not initialized before user is available', () => {
        mockUseAuth.mockReturnValue({
            user: null,
            isLoading: false,
            isAuthenticated: false,
            isLeadership: false,
            isEscalationTeam: false,
            isSupportEngineer: false,
            actualEscalationTeams: [],
            logout: jest.fn(),
        })

        renderProvider()

        expect(screen.getByTestId('initialized')).toHaveTextContent('false')
        expect(screen.getByTestId('effective-teams')).toHaveTextContent('')
    })

    it('returns NO_TEAMS scope when user has no teams', async () => {
        mockUseAuth.mockReturnValue({
            user: {
                id: 'u-1',
                email: 'u1@example.com',
                name: 'User One',
                teams: [],
                roles: [],
            },
            isLoading: false,
            isAuthenticated: true,
            isLeadership: false,
            isEscalationTeam: false,
            isSupportEngineer: false,
            actualEscalationTeams: [],
            logout: jest.fn(),
        })

        renderProvider()

        await waitFor(() =>
            expect(screen.getByTestId('effective-teams')).toHaveTextContent(TEAM_SCOPE.NO_TEAMS)
        )
        expect(screen.getByTestId('initialized')).toHaveTextContent('true')
    })

    it('derives full-access view for role team and exposes only non-role allTeams', async () => {
        mockUseAuth.mockReturnValue({
            user: {
                id: 'u-2',
                email: 'lead@example.com',
                name: 'Leader',
                teams: [
                    { name: 'Support', code: 'support', label: 'Support', types: ['support'] },
                    { name: 'Tenant A', code: 'tenant-a', label: 'Tenant A', types: [] },
                    { name: 'Tenant B', code: 'tenant-b', label: 'Tenant B', types: [] },
                ],
                roles: ['LEADERSHIP'],
            },
            isLoading: false,
            isAuthenticated: true,
            isLeadership: true,
            isEscalationTeam: false,
            isSupportEngineer: false,
            actualEscalationTeams: [],
            logout: jest.fn(),
        })

        renderProvider()

        await waitFor(() => expect(screen.getByTestId('selected-team')).toHaveTextContent('Support'))
        expect(screen.getByTestId('has-full-access')).toHaveTextContent('true')
        expect(screen.getByTestId('effective-teams')).toHaveTextContent('')
        expect(screen.getByTestId('all-teams')).toHaveTextContent('Tenant A|Tenant B')
    })

    it('updates effective teams and full access when selecting a tenant team', async () => {
        mockUseAuth.mockReturnValue({
            user: {
                id: 'u-3',
                email: 'lead2@example.com',
                name: 'Leader Two',
                teams: [
                    { name: 'Support', code: 'support', label: 'Support', types: ['support'] },
                    { name: 'Tenant A', code: 'tenant-a', label: 'Tenant A', types: [] },
                    { name: 'Tenant B', code: 'tenant-b', label: 'Tenant B', types: [] },
                ],
                roles: ['LEADERSHIP'],
            },
            isLoading: false,
            isAuthenticated: true,
            isLeadership: true,
            isEscalationTeam: false,
            isSupportEngineer: false,
            actualEscalationTeams: [],
            logout: jest.fn(),
        })

        renderProvider()

        await waitFor(() => expect(screen.getByTestId('selected-team')).toHaveTextContent('Support'))
        fireEvent.click(screen.getByText('select-tenant-b'))

        await waitFor(() => expect(screen.getByTestId('selected-team')).toHaveTextContent('Tenant B'))
        expect(screen.getByTestId('effective-teams')).toHaveTextContent('Tenant B')
        expect(screen.getByTestId('has-full-access')).toHaveTextContent('false')
    })
})
