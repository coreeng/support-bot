/**
 * TeamFilterContext Unit Tests
 * 
 * Tests the core authorization and team filtering logic:
 * - Initialization based on user role
 * - effectiveTeams computation (which teams to filter data by)
 * - hasFullAccess flag (Leadership/Support get full access, others restricted)
 * - Team selection and switching behavior
 */

import React from 'react';
import { renderHook, act, waitFor } from '@testing-library/react';
import { TeamFilterProvider, useTeamFilter } from '../TeamFilterContext';
import * as UserContext from '../UserContext';

// Mock UserContext
jest.mock('../UserContext');
const mockUseUser = UserContext.useUser as jest.MockedFunction<typeof UserContext.useUser>;

// Mock IframeContext
jest.mock('../IframeContext', () => ({
  useIframe: jest.fn(),
}));
import { useIframe } from '../IframeContext';
const mockUseIframe = useIframe as jest.MockedFunction<() => { isIframe: boolean }>;

// Helper to create a wrapper with mocked contexts
const createWrapper = (
    userContextValue: ReturnType<typeof UserContext.useUser>,
    iframeContextValue: { isIframe: boolean } = { isIframe: false }
) => {
    mockUseUser.mockReturnValue(userContextValue);
    mockUseIframe.mockReturnValue(iframeContextValue);

    const Wrapper = ({ children }: { children: React.ReactNode }) => (
        <TeamFilterProvider>{children}</TeamFilterProvider>
    );
    Wrapper.displayName = 'TeamFilterProviderWrapper';

    return Wrapper;
};

describe('TeamFilterContext', () => {
    beforeEach(() => {
        jest.clearAllMocks();
        // Suppress console.log in tests
        jest.spyOn(console, 'log').mockImplementation(() => {});
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    describe('Initialization', () => {
        it('should initialize selectedTeam to first team when user has teams', async () => {
            const wrapper = createWrapper({
                user: {
                    email: 'user@example.com',
                    teams: [
                        { name: 'Team A', groupRefs: [] },
                        { name: 'Team B', groupRefs: [] }
                    ]
                },
                isLeadership: false,
                isSupportEngineer: false,
                isEscalationTeam: false,
                actualEscalationTeams: [],
                isLoading: false,
                isAuthenticated: true,
                isLoadingEscalationTeams: false
            });

            const { result } = renderHook(() => useTeamFilter(), { wrapper });

            await waitFor(() => {
                expect(result.current.selectedTeam).toBe('Team A');
            });
        });

        it('should initialize selectedTeam to null when user has no teams', async () => {
            const wrapper = createWrapper({
                user: {
                    email: 'user@example.com',
                    teams: []
                },
                isLeadership: false,
                isSupportEngineer: false,
                isEscalationTeam: false,
                actualEscalationTeams: [],
                isLoading: false,
                isAuthenticated: true,
                isLoadingEscalationTeams: false
            });

            const { result } = renderHook(() => useTeamFilter(), { wrapper });

            await waitFor(() => {
                expect(result.current.selectedTeam).toBe(null);
            });
        });

        it('should only initialize once', async () => {
            const wrapper = createWrapper({
                user: {
                    email: 'user@example.com',
                    teams: [{ name: 'Team A', groupRefs: [] }]
                },
                isLeadership: false,
                isSupportEngineer: false,
                isEscalationTeam: false,
                actualEscalationTeams: [],
                isLoading: false,
                isAuthenticated: true,
                isLoadingEscalationTeams: false
            });

            const { result, rerender } = renderHook(() => useTeamFilter(), { wrapper });

            await waitFor(() => {
                expect(result.current.selectedTeam).toBe('Team A');
            });

            // Manually change selectedTeam
            act(() => {
                result.current.setSelectedTeam('Team B');
            });

            expect(result.current.selectedTeam).toBe('Team B');

            // Rerender - should NOT reset to Team A
            rerender();
            expect(result.current.selectedTeam).toBe('Team B');
        });
    });

    describe('effectiveTeams Computation', () => {
        it('should return empty array (view all) when Leadership selects their role group', async () => {
            const wrapper = createWrapper({
                user: {
                    email: 'leader@example.com',
                    teams: [
                        { name: 'Test Support Leadership', groupRefs: [], types: ['leadership'] },
                        { name: 'Team A', groupRefs: [], types: ['tenant'] },
                        { name: 'Team B', groupRefs: [], types: ['tenant'] }
                    ]
                },
                isLeadership: true,
                isSupportEngineer: false,
                isEscalationTeam: false,
                actualEscalationTeams: [],
                isLoading: false,
                isAuthenticated: true,
                isLoadingEscalationTeams: false
            });

            const { result } = renderHook(() => useTeamFilter(), { wrapper });

            await waitFor(() => {
                expect(result.current.selectedTeam).toBe('Test Support Leadership');
            });

            // Empty array indicates "view all" when role group is selected
            expect(result.current.effectiveTeams).toEqual([]);
            expect(result.current.hasFullAccess).toBe(true);
        });

        it('should return empty array (view all) when Support Engineer selects their role group', async () => {
            const wrapper = createWrapper({
                user: {
                    email: 'engineer@example.com',
                    teams: [
                        { name: 'Test Support Engineers', groupRefs: [], types: ['support'] },
                        { name: 'Team A', groupRefs: [], types: ['tenant'] },
                        { name: 'Team B', groupRefs: [], types: ['tenant'] }
                    ]
                },
                isLeadership: false,
                isSupportEngineer: true,
                isEscalationTeam: false,
                actualEscalationTeams: [],
                isLoading: false,
                isAuthenticated: true,
                isLoadingEscalationTeams: false
            });

            const { result } = renderHook(() => useTeamFilter(), { wrapper });

            await waitFor(() => {
                expect(result.current.selectedTeam).toBe('Test Support Engineers');
            });

            // Empty array indicates "view all" when role group is selected
            expect(result.current.effectiveTeams).toEqual([]);
            expect(result.current.hasFullAccess).toBe(true);
        });

        it('should return only selected team when escalation team is selected', async () => {
            const wrapper = createWrapper({
                user: {
                    email: 'escalation@example.com',
                    teams: [
                        { name: 'Core-platform', groupRefs: [], types: ['escalation'] },
                        { name: 'Team A', groupRefs: [], types: ['tenant'] }
                    ]
                },
                isLeadership: false,
                isSupportEngineer: false,
                isEscalationTeam: true,
                actualEscalationTeams: ['Core-platform'],
                isLoading: false,
                isAuthenticated: true,
                isLoadingEscalationTeams: false
            });

            const { result } = renderHook(() => useTeamFilter(), { wrapper });

            await waitFor(() => {
                expect(result.current.selectedTeam).toBe('Core-platform');
            });

            // Should only return the selected escalation team
            expect(result.current.effectiveTeams).toEqual(['Core-platform']);
        });

        it('should return only selected team when regular tenant selects a team', async () => {
            const wrapper = createWrapper({
                user: {
                    email: 'tenant@example.com',
                    teams: [
                        { name: 'Team A', groupRefs: [], types: ['tenant'] },
                        { name: 'Team B', groupRefs: [], types: ['tenant'] }
                    ]
                },
                isLeadership: false,
                isSupportEngineer: false,
                isEscalationTeam: false,
                actualEscalationTeams: [],
                isLoading: false,
                isAuthenticated: true,
                isLoadingEscalationTeams: false
            });

            const { result } = renderHook(() => useTeamFilter(), { wrapper });

            await waitFor(() => {
                expect(result.current.selectedTeam).toBe('Team A');
            });

            expect(result.current.effectiveTeams).toEqual(['Team A']);

            // Switch to Team B
            act(() => {
                result.current.setSelectedTeam('Team B');
            });

            expect(result.current.effectiveTeams).toEqual(['Team B']);
        });

        it('should return empty array (view all) when Leadership role group is selected', async () => {
            const wrapper = createWrapper({
                user: {
                    email: 'user@example.com',
                    teams: [
                        { name: 'CECG Support Leadership', groupRefs: [], types: ['leadership'] },
                        { name: 'CECG Support Engineers', groupRefs: [], types: ['support'] },
                        { name: 'Core-platform', groupRefs: [], types: ['escalation'] },
                        { name: 'Team A', groupRefs: [], types: ['tenant'] }
                    ]
                },
                isLeadership: true,
                isSupportEngineer: true,
                isEscalationTeam: true,
                actualEscalationTeams: ['Core-platform'],
                isLoading: false,
                isAuthenticated: true,
                isLoadingEscalationTeams: false
            });

            const { result } = renderHook(() => useTeamFilter(), { wrapper });

            await waitFor(() => {
                expect(result.current.selectedTeam).toBe('CECG Support Leadership');
            });

            // Empty array indicates "view all" when leadership role group is selected
            expect(result.current.effectiveTeams).toEqual([]);
            expect(result.current.hasFullAccess).toBe(true);
        });

        it('should return empty array when no user', () => {
            const wrapper = createWrapper({
                user: null,
                isLeadership: false,
                isSupportEngineer: false,
                isEscalationTeam: false,
                actualEscalationTeams: [],
                isLoading: false,
                isAuthenticated: false,
                isLoadingEscalationTeams: false
            });

            const { result } = renderHook(() => useTeamFilter(), { wrapper });

            expect(result.current.effectiveTeams).toEqual([]);
        });
    });

    describe('hasFullAccess Flag', () => {
        it('should be true when Leadership selects their role group', async () => {
            const wrapper = createWrapper({
                user: {
                    email: 'leader@example.com',
                    teams: [
                        { name: 'Test Support Leadership', groupRefs: [], types: ['leadership'] },
                        { name: 'Team A', groupRefs: [], types: ['tenant'] }
                    ]
                },
                isLeadership: true,
                isSupportEngineer: false,
                isEscalationTeam: false,
                actualEscalationTeams: [],
                isLoading: false,
                isAuthenticated: true,
                isLoadingEscalationTeams: false
            });

            const { result } = renderHook(() => useTeamFilter(), { wrapper });

            await waitFor(() => {
                expect(result.current.selectedTeam).toBe('Test Support Leadership');
            });

            expect(result.current.hasFullAccess).toBe(true);
        });

        it('should be true when Support Engineer selects their role group', async () => {
            const wrapper = createWrapper({
                user: {
                    email: 'engineer@example.com',
                    teams: [
                        { name: 'Test Support Engineers', groupRefs: [], types: ['support'] },
                        { name: 'Team A', groupRefs: [], types: ['tenant'] }
                    ]
                },
                isLeadership: false,
                isSupportEngineer: true,
                isEscalationTeam: false,
                actualEscalationTeams: [],
                isLoading: false,
                isAuthenticated: true,
                isLoadingEscalationTeams: false
            });

            const { result } = renderHook(() => useTeamFilter(), { wrapper });

            await waitFor(() => {
                expect(result.current.selectedTeam).toBe('Test Support Engineers');
            });

            expect(result.current.hasFullAccess).toBe(true);
        });

        it('should be false when escalation team is selected (even for Leadership)', async () => {
            const wrapper = createWrapper({
                user: {
                    email: 'leader@example.com',
                    teams: [
                        { name: 'Leadership', groupRefs: [], types: ['leadership'] },
                        { name: 'Core-platform', groupRefs: [], types: ['escalation'] }
                    ]
                },
                isLeadership: true,
                isSupportEngineer: false,
                isEscalationTeam: true,
                actualEscalationTeams: ['Core-platform'],
                isLoading: false,
                isAuthenticated: true,
                isLoadingEscalationTeams: false
            });

            const { result } = renderHook(() => useTeamFilter(), { wrapper });

            await waitFor(() => {
                expect(result.current.selectedTeam).toBe('Leadership');
            });

            // Switch to escalation team
            act(() => {
                result.current.setSelectedTeam('Core-platform');
            });

            // Should be false when escalation team selected
            expect(result.current.hasFullAccess).toBe(false);
        });

        it('should be false for regular tenants', async () => {
            const wrapper = createWrapper({
                user: {
                    email: 'tenant@example.com',
                    teams: [{ name: 'Team A', groupRefs: [] }]
                },
                isLeadership: false,
                isSupportEngineer: false,
                isEscalationTeam: false,
                actualEscalationTeams: [],
                isLoading: false,
                isAuthenticated: true,
                isLoadingEscalationTeams: false
            });

            const { result } = renderHook(() => useTeamFilter(), { wrapper });

            await waitFor(() => {
                expect(result.current.selectedTeam).toBe('Team A');
            });

            expect(result.current.hasFullAccess).toBe(false);
        });

        it('should be restricted when regular team is selected by Leadership', async () => {
            const wrapper = createWrapper({
                user: {
                    email: 'leader@example.com',
                    teams: [
                        { name: 'Leadership', groupRefs: [], types: ['leadership'] },
                        { name: 'Team A', groupRefs: [], types: ['tenant'] }
                    ]
                },
                isLeadership: true,
                isSupportEngineer: false,
                isEscalationTeam: false,
                actualEscalationTeams: [],
                isLoading: false,
                isAuthenticated: true,
                isLoadingEscalationTeams: false
            });

            const { result } = renderHook(() => useTeamFilter(), { wrapper });

            // Switch to regular team
            act(() => {
                result.current.setSelectedTeam('Team A');
            });

            // Leadership selecting a non-role tenant team should be restricted
            expect(result.current.hasFullAccess).toBe(false);
        });
    });

    describe('Team Selection Behavior', () => {
        it('should allow changing selectedTeam', async () => {
            const wrapper = createWrapper({
                user: {
                    email: 'user@example.com',
                    teams: [
                        { name: 'Team A', groupRefs: [] },
                        { name: 'Team B', groupRefs: [] }
                    ]
                },
                isLeadership: false,
                isSupportEngineer: false,
                isEscalationTeam: false,
                actualEscalationTeams: [],
                isLoading: false,
                isAuthenticated: true,
                isLoadingEscalationTeams: false
            });

            const { result } = renderHook(() => useTeamFilter(), { wrapper });

            await waitFor(() => {
                expect(result.current.selectedTeam).toBe('Team A');
            });

            act(() => {
                result.current.setSelectedTeam('Team B');
            });

            expect(result.current.selectedTeam).toBe('Team B');
        });

        it('should update effectiveTeams when team selection changes', async () => {
            const wrapper = createWrapper({
                user: {
                    email: 'user@example.com',
                    teams: [
                        { name: 'Team A', groupRefs: [] },
                        { name: 'Team B', groupRefs: [] }
                    ]
                },
                isLeadership: false,
                isSupportEngineer: false,
                isEscalationTeam: false,
                actualEscalationTeams: [],
                isLoading: false,
                isAuthenticated: true,
                isLoadingEscalationTeams: false
            });

            const { result } = renderHook(() => useTeamFilter(), { wrapper });

            await waitFor(() => {
                expect(result.current.selectedTeam).toBe('Team A');
            });

            expect(result.current.effectiveTeams).toEqual(['Team A']);

            act(() => {
                result.current.setSelectedTeam('Team B');
            });

            expect(result.current.effectiveTeams).toEqual(['Team B']);
        });

        it('should allow setting selectedTeam to null', async () => {
            const wrapper = createWrapper({
                user: {
                    email: 'leader@example.com',
                    teams: [
                        { name: 'Leadership', groupRefs: [], types: ['leadership'] },
                        { name: 'Team A', groupRefs: [], types: ['tenant'] }
                    ]
                },
                isLeadership: true,
                isSupportEngineer: false,
                isEscalationTeam: false,
                actualEscalationTeams: [],
                isLoading: false,
                isAuthenticated: true,
                isLoadingEscalationTeams: false
            });

            const { result } = renderHook(() => useTeamFilter(), { wrapper });

            await waitFor(() => {
                expect(result.current.selectedTeam).toBe('Leadership');
            });

            act(() => {
                result.current.setSelectedTeam(null);
            });

            expect(result.current.selectedTeam).toBe(null);
            // Leadership with null should have full access
            expect(result.current.hasFullAccess).toBe(true);
        });
    });

    describe('Edge Cases', () => {
        it('should handle user with no teams', async () => {
            const wrapper = createWrapper({
                user: {
                    email: 'user@example.com',
                    teams: []
                },
                isLeadership: false,
                isSupportEngineer: false,
                isEscalationTeam: false,
                actualEscalationTeams: [],
                isLoading: false,
                isAuthenticated: true,
                isLoadingEscalationTeams: false
            });

            const { result } = renderHook(() => useTeamFilter(), { wrapper });

            await waitFor(() => {
                expect(result.current.selectedTeam).toBe(null);
            });

            expect(result.current.effectiveTeams).toEqual([]);
            expect(result.current.hasFullAccess).toBe(false);
        });

        it('should handle null user gracefully', () => {
            const wrapper = createWrapper({
                user: null,
                isLeadership: false,
                isSupportEngineer: false,
                isEscalationTeam: false,
                actualEscalationTeams: [],
                isLoading: false,
                isAuthenticated: false,
                isLoadingEscalationTeams: false
            });

            const { result } = renderHook(() => useTeamFilter(), { wrapper });

            expect(result.current.selectedTeam).toBe(null);
            expect(result.current.effectiveTeams).toEqual([]);
            expect(result.current.hasFullAccess).toBe(false);
        });

        it('should throw error when used outside provider', () => {
            // Suppress console.error for this test
            jest.spyOn(console, 'error').mockImplementation(() => {});

            expect(() => {
                renderHook(() => useTeamFilter());
            }).toThrow('useTeamFilter must be used within a TeamFilterProvider');
        });
    });
});

