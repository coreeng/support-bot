import { renderHook, waitFor } from '@testing-library/react';
import { useUser, UserProvider } from '../UserContext';
import { useSession } from 'next-auth/react';
import * as backendHooks from '../../lib/hooks/backend';

// Mock dependencies
jest.mock('next-auth/react');
jest.mock('../../lib/hooks/backend');

const mockUseSession = useSession as jest.MockedFunction<typeof useSession>;
const mockUseEscalationTeams = backendHooks.useEscalationTeams as jest.MockedFunction<typeof backendHooks.useEscalationTeams>;

describe('UserContext', () => {
    beforeEach(() => {
        jest.clearAllMocks();
        // Suppress console.log in tests
        jest.spyOn(console, 'log').mockImplementation(() => {});
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    const mockL2Teams = [
        { name: 'Core-platform', types: ['escalation'] },
        { name: 'Core-networking', types: ['escalation'] },
        { name: 'Data-platform', types: ['escalation'] }
    ];

    const mockUserTeams = [
        { name: 'Core-platform', groupRefs: [] },
        { name: 'Team A', groupRefs: [] }
    ];

    describe('Loading State', () => {
        it('should show loading when session is loading', () => {
            mockUseSession.mockReturnValue({
                status: 'loading',
                data: null,
                update: jest.fn()
            });

            mockUseEscalationTeams.mockReturnValue({
                data: undefined,
                isLoading: true,
                error: null,
                refetch: jest.fn(),
                isError: false,
                isFetching: false,
                isSuccess: false
            } as unknown as ReturnType<typeof backendHooks.useEscalationTeams>);

            const { result } = renderHook(() => useUser(), {
                wrapper: UserProvider
            });

            expect(result.current.isLoading).toBe(true);
            expect(result.current.isAuthenticated).toBe(false);
            expect(result.current.user).toBeNull();
        });

        it('should NOT show loading when session is ready but L2 teams are still loading', () => {
            // Note: isLoading only tracks session loading, not L2 teams loading
            // This is by design - the app can still render with user info while L2 teams load in background
            mockUseSession.mockReturnValue({
                status: 'authenticated',
                data: {
                    user: {
                        email: 'escalation@example.com',
                        name: 'Escalation User',
                        teams: mockUserTeams,
                        isLeadership: false,
                        isEscalation: true,
                        isSupportEngineer: false
                    },
                    expires: '2025-12-31'
                },
                update: jest.fn()
            });

            mockUseEscalationTeams.mockReturnValue({
                data: undefined,
                isLoading: true,
                error: null,
                refetch: jest.fn(),
                isError: false,
                isFetching: false,
                isSuccess: false
            } as unknown as ReturnType<typeof backendHooks.useEscalationTeams>);

            const { result } = renderHook(() => useUser(), {
                wrapper: UserProvider
            });

            // isLoading only reflects session loading state
            expect(result.current.isLoading).toBe(false);
            expect(result.current.isAuthenticated).toBe(true);
            // actualEscalationTeams will be empty until L2 teams finish loading
            expect(result.current.actualEscalationTeams).toEqual([]);
        });
    });

    describe('Unauthenticated State', () => {
        it('should handle unauthenticated user', () => {
            mockUseSession.mockReturnValue({
                status: 'unauthenticated',
                data: null,
                update: jest.fn()
            });

            mockUseEscalationTeams.mockReturnValue({
                data: undefined,
                isLoading: false,
                error: null,
                refetch: jest.fn(),
                isError: false,
                isFetching: false,
                isSuccess: false
            } as unknown as ReturnType<typeof backendHooks.useEscalationTeams>);

            const { result } = renderHook(() => useUser(), {
                wrapper: UserProvider
            });

            expect(result.current.isLoading).toBe(false);
            expect(result.current.isAuthenticated).toBe(false);
            expect(result.current.user).toBeNull();
            expect(result.current.isLeadership).toBe(false);
            expect(result.current.isEscalationTeam).toBe(false);
            expect(result.current.isSupportEngineer).toBe(false);
            expect(result.current.actualEscalationTeams).toEqual([]);
        });
    });

    describe('Authenticated User - Role Flags', () => {
        it('should correctly set leadership flag', () => {
            mockUseSession.mockReturnValue({
                status: 'authenticated',
                data: {
                    user: {
                        email: 'leader@example.com',
                        name: 'Leader',
                        teams: [{ name: 'Leadership', groupRefs: [] }],
                        isLeadership: true,
                        isEscalation: false,
                        isSupportEngineer: false
                    },
                    expires: '2025-12-31'
                },
                update: jest.fn()
            });

            mockUseEscalationTeams.mockReturnValue({
                data: [],
                isLoading: false,
                error: null,
                refetch: jest.fn(),
                isError: false,
                isFetching: false,
                isSuccess: true
            } as unknown as ReturnType<typeof backendHooks.useEscalationTeams>);

            const { result } = renderHook(() => useUser(), {
                wrapper: UserProvider
            });

            expect(result.current.isAuthenticated).toBe(true);
            expect(result.current.isLeadership).toBe(true);
            expect(result.current.isEscalationTeam).toBe(false);
            expect(result.current.isSupportEngineer).toBe(false);
        });

        it('should correctly set support engineer flag', () => {
            mockUseSession.mockReturnValue({
                status: 'authenticated',
                data: {
                    user: {
                        email: 'engineer@example.com',
                        name: 'Engineer',
                        teams: [{ name: 'Support Engineers', groupRefs: [] }],
                        isLeadership: false,
                        isEscalation: false,
                        isSupportEngineer: true
                    },
                    expires: '2025-12-31'
                },
                update: jest.fn()
            });

            mockUseEscalationTeams.mockReturnValue({
                data: [],
                isLoading: false,
                error: null,
                refetch: jest.fn(),
                isError: false,
                isFetching: false,
                isSuccess: true
            } as unknown as ReturnType<typeof backendHooks.useEscalationTeams>);

            const { result } = renderHook(() => useUser(), {
                wrapper: UserProvider
            });

            expect(result.current.isAuthenticated).toBe(true);
            expect(result.current.isLeadership).toBe(false);
            expect(result.current.isEscalationTeam).toBe(false);
            expect(result.current.isSupportEngineer).toBe(true);
        });

        it('should handle multiple roles (leadership + support engineer)', () => {
            mockUseSession.mockReturnValue({
                status: 'authenticated',
                data: {
                    user: {
                        email: 'multi@example.com',
                        name: 'Multi Role',
                        teams: [
                            { name: 'Leadership', groupRefs: [] },
                            { name: 'Support Engineers', groupRefs: [] }
                        ],
                        isLeadership: true,
                        isEscalation: false,
                        isSupportEngineer: true
                    },
                    expires: '2025-12-31'
                },
                update: jest.fn()
            });

            mockUseEscalationTeams.mockReturnValue({
                data: [],
                isLoading: false,
                error: null,
                refetch: jest.fn(),
                isError: false,
                isFetching: false,
                isSuccess: true
            } as unknown as ReturnType<typeof backendHooks.useEscalationTeams>);

            const { result } = renderHook(() => useUser(), {
                wrapper: UserProvider
            });

            expect(result.current.isLeadership).toBe(true);
            expect(result.current.isSupportEngineer).toBe(true);
        });
    });

    describe('actualEscalationTeams Computation', () => {
        it('should return empty array when user is not escalation team member', () => {
            mockUseSession.mockReturnValue({
                status: 'authenticated',
                data: {
                    user: {
                        email: 'regular@example.com',
                        name: 'Regular User',
                        teams: mockUserTeams,
                        isLeadership: false,
                        isEscalation: false,
                        isSupportEngineer: false
                    },
                    expires: '2025-12-31'
                },
                update: jest.fn()
            });

            mockUseEscalationTeams.mockReturnValue({
                data: mockL2Teams,
                isLoading: false,
                error: null,
                refetch: jest.fn(),
                isError: false,
                isFetching: false,
                isSuccess: true
            } as unknown as ReturnType<typeof backendHooks.useEscalationTeams>);

            const { result } = renderHook(() => useUser(), {
                wrapper: UserProvider
            });

            expect(result.current.actualEscalationTeams).toEqual([]);
        });

        it('should compute actualEscalationTeams by matching user teams with L2 teams', () => {
            mockUseSession.mockReturnValue({
                status: 'authenticated',
                data: {
                    user: {
                        email: 'escalation@example.com',
                        name: 'Escalation User',
                        teams: [
                            { name: 'Core-platform', groupRefs: [] },
                            { name: 'Core-networking', groupRefs: [] },
                            { name: 'Team A', groupRefs: [] }
                        ],
                        isLeadership: false,
                        isEscalation: true,
                        isSupportEngineer: false
                    },
                    expires: '2025-12-31'
                },
                update: jest.fn()
            });

            mockUseEscalationTeams.mockReturnValue({
                data: mockL2Teams,
                isLoading: false,
                error: null,
                refetch: jest.fn(),
                isError: false,
                isFetching: false,
                isSuccess: true
            } as unknown as ReturnType<typeof backendHooks.useEscalationTeams>);

            const { result } = renderHook(() => useUser(), {
                wrapper: UserProvider
            });

            expect(result.current.actualEscalationTeams).toEqual([
                'Core-platform',
                'Core-networking'
            ]);
        });

        it('should handle case where escalation user is not in any L2 team', () => {
            mockUseSession.mockReturnValue({
                status: 'authenticated',
                data: {
                    user: {
                        email: 'escalation@example.com',
                        name: 'Escalation User',
                        teams: [
                            { name: 'Team A', groupRefs: [] },
                            { name: 'Team B', groupRefs: [] }
                        ],
                        isLeadership: false,
                        isEscalation: true,
                        isSupportEngineer: false
                    },
                    expires: '2025-12-31'
                },
                update: jest.fn()
            });

            mockUseEscalationTeams.mockReturnValue({
                data: mockL2Teams,
                isLoading: false,
                error: null,
                refetch: jest.fn(),
                isError: false,
                isFetching: false,
                isSuccess: true
            } as unknown as ReturnType<typeof backendHooks.useEscalationTeams>);

            const { result } = renderHook(() => useUser(), {
                wrapper: UserProvider
            });

            expect(result.current.actualEscalationTeams).toEqual([]);
        });

        it('should return empty array when L2 teams list is empty', () => {
            mockUseSession.mockReturnValue({
                status: 'authenticated',
                data: {
                    user: {
                        email: 'escalation@example.com',
                        name: 'Escalation User',
                        teams: [{ name: 'Core-platform', groupRefs: [] }],
                        isLeadership: false,
                        isEscalation: true,
                        isSupportEngineer: false
                    },
                    expires: '2025-12-31'
                },
                update: jest.fn()
            });

            mockUseEscalationTeams.mockReturnValue({
                data: [],
                isLoading: false,
                error: null,
                refetch: jest.fn(),
                isError: false,
                isFetching: false,
                isSuccess: true
            } as unknown as ReturnType<typeof backendHooks.useEscalationTeams>);

            const { result } = renderHook(() => useUser(), {
                wrapper: UserProvider
            });

            expect(result.current.actualEscalationTeams).toEqual([]);
        });

        it('should return empty array when L2 teams are undefined', () => {
            mockUseSession.mockReturnValue({
                status: 'authenticated',
                data: {
                    user: {
                        email: 'escalation@example.com',
                        name: 'Escalation User',
                        teams: [{ name: 'Core-platform', groupRefs: [] }],
                        isLeadership: false,
                        isEscalation: true,
                        isSupportEngineer: false
                    },
                    expires: '2025-12-31'
                },
                update: jest.fn()
            });

            mockUseEscalationTeams.mockReturnValue({
                data: undefined,
                isLoading: false,
                error: null,
                refetch: jest.fn(),
                isError: false,
                isFetching: false,
                isSuccess: false
            } as unknown as ReturnType<typeof backendHooks.useEscalationTeams>);

            const { result } = renderHook(() => useUser(), {
                wrapper: UserProvider
            });

            expect(result.current.actualEscalationTeams).toEqual([]);
        });

        it('should return empty array when user has no teams', () => {
            mockUseSession.mockReturnValue({
                status: 'authenticated',
                data: {
                    user: {
                        email: 'escalation@example.com',
                        name: 'Escalation User',
                        teams: [],
                        isLeadership: false,
                        isEscalation: true,
                        isSupportEngineer: false
                    },
                    expires: '2025-12-31'
                },
                update: jest.fn()
            });

            mockUseEscalationTeams.mockReturnValue({
                data: mockL2Teams,
                isLoading: false,
                error: null,
                refetch: jest.fn(),
                isError: false,
                isFetching: false,
                isSuccess: true
            } as unknown as ReturnType<typeof backendHooks.useEscalationTeams>);

            const { result } = renderHook(() => useUser(), {
                wrapper: UserProvider
            });

            expect(result.current.actualEscalationTeams).toEqual([]);
        });
    });

    describe('User Object', () => {
        it('should provide user object with email and teams when authenticated', () => {
            // Note: UserContext returns a simplified user object with only email and teams
            // Role flags (isLeadership, etc.) are available as separate properties on the context
            const mockSession = {
                user: {
                    email: 'test@example.com',
                    name: 'Test User',
                    teams: mockUserTeams,
                    isLeadership: true,
                    isEscalation: false,
                    isSupportEngineer: true
                },
                expires: '2025-12-31'
            };

            mockUseSession.mockReturnValue({
                status: 'authenticated',
                data: mockSession,
                update: jest.fn()
            });

            mockUseEscalationTeams.mockReturnValue({
                data: mockL2Teams,
                isLoading: false,
                error: null,
                refetch: jest.fn(),
                isError: false,
                isFetching: false,
                isSuccess: true
            } as unknown as ReturnType<typeof backendHooks.useEscalationTeams>);

            const { result } = renderHook(() => useUser(), {
                wrapper: UserProvider
            });

            // User object only contains email and teams
            expect(result.current.user).toEqual({
                email: 'test@example.com',
                teams: mockUserTeams
            });
            expect(result.current.user?.email).toBe('test@example.com');
            expect(result.current.user?.teams).toEqual(mockUserTeams);
            
            // Role flags are separate properties on the context
            expect(result.current.isLeadership).toBe(true);
            expect(result.current.isSupportEngineer).toBe(true);
            expect(result.current.isEscalationTeam).toBe(false);
        });

        it('should return null user when unauthenticated', () => {
            mockUseSession.mockReturnValue({
                status: 'unauthenticated',
                data: null,
                update: jest.fn()
            });

            mockUseEscalationTeams.mockReturnValue({
                data: undefined,
                isLoading: false,
                error: null,
                refetch: jest.fn(),
                isError: false,
                isFetching: false,
                isSuccess: false
            } as unknown as ReturnType<typeof backendHooks.useEscalationTeams>);

            const { result } = renderHook(() => useUser(), {
                wrapper: UserProvider
            });

            expect(result.current.user).toBeNull();
        });
    });

    describe('Session Status Updates', () => {
        it('should update when session changes from loading to authenticated', async () => {
            const { rerender, result } = renderHook(() => useUser(), {
                wrapper: UserProvider
            });

            // Initially loading
            mockUseSession.mockReturnValue({
                status: 'loading',
                data: null,
                update: jest.fn()
            });

            mockUseEscalationTeams.mockReturnValue({
                data: undefined,
                isLoading: true,
                error: null,
                refetch: jest.fn(),
                isError: false,
                isFetching: false,
                isSuccess: false
            } as unknown as ReturnType<typeof backendHooks.useEscalationTeams>);

            rerender();
            expect(result.current.isLoading).toBe(true);
            expect(result.current.isAuthenticated).toBe(false);

            // Then authenticated
            mockUseSession.mockReturnValue({
                status: 'authenticated',
                data: {
                    user: {
                        email: 'user@example.com',
                        name: 'User',
                        teams: mockUserTeams,
                        isLeadership: false,
                        isEscalation: true,
                        isSupportEngineer: false
                    },
                    expires: '2025-12-31'
                },
                update: jest.fn()
            });

            mockUseEscalationTeams.mockReturnValue({
                data: mockL2Teams,
                isLoading: false,
                error: null,
                refetch: jest.fn(),
                isError: false,
                isFetching: false,
                isSuccess: true
            } as unknown as ReturnType<typeof backendHooks.useEscalationTeams>);

            rerender();

            await waitFor(() => {
                expect(result.current.isLoading).toBe(false);
                expect(result.current.isAuthenticated).toBe(true);
                expect(result.current.user?.email).toBe('user@example.com');
            });
        });
    });

    describe('Complex Scenarios', () => {
        it('should handle leadership user who is also in an L2 team', () => {
            mockUseSession.mockReturnValue({
                status: 'authenticated',
                data: {
                    user: {
                        email: 'leader@example.com',
                        name: 'Leader',
                        teams: [
                            { name: 'Leadership', groupRefs: [] },
                            { name: 'Core-platform', groupRefs: [] }
                        ],
                        isLeadership: true,
                        isEscalation: true,
                        isSupportEngineer: false
                    },
                    expires: '2025-12-31'
                },
                update: jest.fn()
            });

            mockUseEscalationTeams.mockReturnValue({
                data: mockL2Teams,
                isLoading: false,
                error: null,
                refetch: jest.fn(),
                isError: false,
                isFetching: false,
                isSuccess: true
            } as unknown as ReturnType<typeof backendHooks.useEscalationTeams>);

            const { result } = renderHook(() => useUser(), {
                wrapper: UserProvider
            });

            expect(result.current.isLeadership).toBe(true);
            expect(result.current.isEscalationTeam).toBe(true);
            expect(result.current.actualEscalationTeams).toEqual(['Core-platform']);
        });

        it('should handle user in multiple L2 teams', () => {
            mockUseSession.mockReturnValue({
                status: 'authenticated',
                data: {
                    user: {
                        email: 'multi-escalation@example.com',
                        name: 'Multi Escalation',
                        teams: [
                            { name: 'Core-platform', groupRefs: [] },
                            { name: 'Core-networking', groupRefs: [] },
                            { name: 'Data-platform', groupRefs: [] }
                        ],
                        isLeadership: false,
                        isEscalation: true,
                        isSupportEngineer: false
                    },
                    expires: '2025-12-31'
                },
                update: jest.fn()
            });

            mockUseEscalationTeams.mockReturnValue({
                data: mockL2Teams,
                isLoading: false,
                error: null,
                refetch: jest.fn(),
                isError: false,
                isFetching: false,
                isSuccess: true
            } as unknown as ReturnType<typeof backendHooks.useEscalationTeams>);

            const { result } = renderHook(() => useUser(), {
                wrapper: UserProvider
            });

            expect(result.current.actualEscalationTeams).toEqual([
                'Core-platform',
                'Core-networking',
                'Data-platform'
            ]);
        });

        it('should handle all three roles at once', () => {
            mockUseSession.mockReturnValue({
                status: 'authenticated',
                data: {
                    user: {
                        email: 'superuser@example.com',
                        name: 'Super User',
                        teams: [
                            { name: 'Leadership', groupRefs: [] },
                            { name: 'Support Engineers', groupRefs: [] },
                            { name: 'Core-platform', groupRefs: [] }
                        ],
                        isLeadership: true,
                        isEscalation: true,
                        isSupportEngineer: true
                    },
                    expires: '2025-12-31'
                },
                update: jest.fn()
            });

            mockUseEscalationTeams.mockReturnValue({
                data: mockL2Teams,
                isLoading: false,
                error: null,
                refetch: jest.fn(),
                isError: false,
                isFetching: false,
                isSuccess: true
            } as unknown as ReturnType<typeof backendHooks.useEscalationTeams>);

            const { result } = renderHook(() => useUser(), {
                wrapper: UserProvider
            });

            expect(result.current.isLeadership).toBe(true);
            expect(result.current.isSupportEngineer).toBe(true);
            expect(result.current.isEscalationTeam).toBe(true);
            expect(result.current.actualEscalationTeams).toEqual(['Core-platform']);
        });
    });

    describe('Error Handling', () => {
        it('should handle useSession returning null data gracefully', () => {
            // Edge case: status is 'unauthenticated' with null data
            mockUseSession.mockReturnValue({
                status: 'unauthenticated',
                data: null,
                update: jest.fn()
            });

            mockUseEscalationTeams.mockReturnValue({
                data: mockL2Teams,
                isLoading: false,
                error: null,
                refetch: jest.fn(),
                isError: false,
                isFetching: false,
                isSuccess: true
            } as unknown as ReturnType<typeof backendHooks.useEscalationTeams>);

            const { result } = renderHook(() => useUser(), {
                wrapper: UserProvider
            });

            // Should show as unauthenticated with null user
            expect(result.current.isAuthenticated).toBe(false);
            expect(result.current.user).toBeNull();
            expect(result.current.actualEscalationTeams).toEqual([]);
            // All role flags default to false when unauthenticated
            expect(result.current.isLeadership).toBe(false);
            expect(result.current.isEscalationTeam).toBe(false);
            expect(result.current.isSupportEngineer).toBe(false);
        });

        it('should handle L2 teams fetch error gracefully', () => {
            mockUseSession.mockReturnValue({
                status: 'authenticated',
                data: {
                    user: {
                        email: 'escalation@example.com',
                        name: 'Escalation User',
                        teams: [{ name: 'Core-platform', groupRefs: [] }],
                        isLeadership: false,
                        isEscalation: true,
                        isSupportEngineer: false
                    },
                    expires: '2025-12-31'
                },
                update: jest.fn()
            });

            mockUseEscalationTeams.mockReturnValue({
                data: undefined,
                isLoading: false,
                error: new Error('Network error'),
                refetch: jest.fn(),
                isError: true,
                isFetching: false,
                isSuccess: false
            } as unknown as ReturnType<typeof backendHooks.useEscalationTeams>);

            const { result } = renderHook(() => useUser(), {
                wrapper: UserProvider
            });

            // Should still be authenticated but with empty escalation teams
            expect(result.current.isAuthenticated).toBe(true);
            expect(result.current.isEscalationTeam).toBe(true);
            expect(result.current.actualEscalationTeams).toEqual([]);
        });
    });

    describe('Context Usage', () => {
        it('should throw error when useUser is called outside provider', () => {
            // Temporarily suppress console.error for this test
            const consoleError = jest.spyOn(console, 'error').mockImplementation(() => {});

            expect(() => {
                renderHook(() => useUser());
            }).toThrow('useUser must be used within a UserProvider');

            consoleError.mockRestore();
        });
    });
});

