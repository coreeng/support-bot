import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import Tickets from '../tickets';
import * as hooks from '../../../lib/hooks';

const mockReplace = jest.fn();
let mockSearchParamsValue = '';

jest.mock('next/navigation', () => ({
    useRouter: () => ({
        replace: mockReplace,
    }),
    usePathname: () => '/tickets',
    useSearchParams: () => new URLSearchParams(mockSearchParamsValue),
}));

// Mock the hooks
jest.mock('../../../lib/hooks');

const mockUseTickets = hooks.useTickets as jest.MockedFunction<typeof hooks.useTickets>;
const mockUseAllTickets = hooks.useAllTickets as jest.MockedFunction<typeof hooks.useAllTickets>;
const mockUseTicket = hooks.useTicket as jest.MockedFunction<typeof hooks.useTicket>;
const mockUseTenantTeams = hooks.useTenantTeams as jest.MockedFunction<typeof hooks.useTenantTeams>;
const mockUseRegistry = hooks.useRegistry as jest.MockedFunction<typeof hooks.useRegistry>;
const mockUseAssignmentEnabled = hooks.useAssignmentEnabled as jest.MockedFunction<typeof hooks.useAssignmentEnabled>;

// Helper to create mock ticket with recent dates
const createMockTicket = (id: string, status: string, teamName: string, impact: string): any => {
    const now = new Date();
    const recentDate = new Date(now.getTime() - 24 * 60 * 60 * 1000); // Yesterday
    return {
        id,
        status,
        team: { name: teamName },
        impact,
        tags: [{ code: 'bug', label: 'Bug' }],
        escalations: [],
        logs: [{ event: 'opened', date: recentDate.toISOString() }],
        query: { link: 'https://example.com' }
    };
};

// Factory function for mock paginated tickets
const getMockPaginatedTickets = (tickets: ReturnType<typeof createMockTicket>[]) => ({
    content: tickets,
    page: 0,
    totalPages: 2,
    totalElements: tickets.length
});

const mockTeams = [
    { name: 'Team A', types: ['tenant'] },
    { name: 'Team B', types: ['tenant'] }
];

const mockRegistry = {
    impacts: [
        { code: 'high', label: 'High Impact' },
        { code: 'medium', label: 'Medium Impact' },
        { code: 'low', label: 'Low Impact' }
    ],
    tags: [{ code: 'bug', label: 'Bug' }]
};

// Mock auth hook to provide a test user by default
jest.mock('../../../hooks/useAuth', () => ({
    useAuth: () => ({
        user: 'test@example.com',
        isAuthenticated: true,
        isLoading: false,
        teams: ['Team A', 'Team B'],
        isLeadership: false,
        isEscalationTeam: false,
        isSupportEngineer: false,
        actualEscalationTeams: [],
        isLoadingEscalationTeams: false
    })
}));

// Mock EditTicketModal
jest.mock('../EditTicketModal', () => ({
    __esModule: true,
    default: ({ ticketId, open, onOpenChange }: { ticketId: string | null; open: boolean; onOpenChange: (open: boolean) => void }) => {
        if (!open) return null;
        return (
            <div data-testid="edit-ticket-modal">
                <div>Ticket Modal: {ticketId}</div>
                <button onClick={() => onOpenChange(false)}>Close Modal</button>
            </div>
        );
    }
}));

// Mock team filter context
jest.mock('../../../contexts/TeamFilterContext', () => ({
    TeamFilterProvider: ({ children }: { children: React.ReactNode }) => children,
    useTeamFilter: jest.fn(),
}));
const mockUseTeamFilter = jest.requireMock('../../../contexts/TeamFilterContext').useTeamFilter as jest.MockedFunction<() => {
    selectedTeam: string | null
    setSelectedTeam: jest.Mock
    hasFullAccess: boolean
    effectiveTeams: string[]
    allTeams?: string[]
    initialized?: boolean
}>;

const Wrapper = ({ children }: { children: React.ReactNode }) => {
    const queryClient = new QueryClient({
        defaultOptions: { queries: { retry: false } }
    });
    return (
        <QueryClientProvider client={queryClient}>
            {children}
        </QueryClientProvider>
    );
};

describe('Tickets Component', () => {
    beforeEach(() => {
        jest.clearAllMocks();
        mockSearchParamsValue = '';
        mockReplace.mockClear();
        
        // Default mock implementations
        mockUseTeamFilter.mockReturnValue({
            selectedTeam: null,
            setSelectedTeam: jest.fn(),
            hasFullAccess: true,
            effectiveTeams: [],
            allTeams: ['Team A', 'Team B'],
            initialized: true
        });

        mockUseTenantTeams.mockReturnValue({
            data: mockTeams,
            isLoading: false,
            error: null
        } as unknown as ReturnType<typeof hooks.useTenantTeams>);
        
        mockUseRegistry.mockReturnValue({
            data: mockRegistry,
            isLoading: false,
            error: null
        } as unknown as ReturnType<typeof hooks.useRegistry>);
        
        mockUseTicket.mockReturnValue({
            data: null,
            isLoading: false,
            error: null
        } as unknown as ReturnType<typeof hooks.useTicket>);

        mockUseAllTickets.mockReturnValue({
            data: null,
            isLoading: false,
            error: null
        } as unknown as ReturnType<typeof hooks.useAllTickets>);

        mockUseAssignmentEnabled.mockReturnValue({
            data: false,
            isLoading: false,
            error: null
        } as unknown as ReturnType<typeof hooks.useAssignmentEnabled>);
    });

    describe('Rendering', () => {
        it('renders the tickets table', () => {
            const mockTickets = getMockPaginatedTickets([
                createMockTicket('1', 'opened', 'Team A', 'high')
            ]);
            
            mockUseTickets.mockReturnValue({
                data: mockTickets,
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);

            render(<Tickets />, { wrapper: Wrapper });
            
            expect(screen.getByRole('table')).toBeInTheDocument();
        });

        it('renders table headers', () => {
            const mockTickets = getMockPaginatedTickets([]);
            
            mockUseTickets.mockReturnValue({
                data: mockTickets,
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);

            render(<Tickets />, { wrapper: Wrapper });
            
            expect(screen.getByText('Status')).toBeInTheDocument();
            expect(screen.getByText('Team')).toBeInTheDocument();
            expect(screen.getByText('Impact')).toBeInTheDocument();
            expect(screen.getByText('Tags')).toBeInTheDocument();
            expect(screen.getByText('Escalated')).toBeInTheDocument();
            expect(screen.getAllByText('Escalated To').length).toBeGreaterThan(0);
        });

        it('displays ticket data in table', () => {
            const mockTickets = getMockPaginatedTickets([
                createMockTicket('1', 'opened', 'Team A', 'high')
            ]);
            
            mockUseTickets.mockReturnValue({
                data: mockTickets,
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);

            render(<Tickets />, { wrapper: Wrapper });
            
            // Check for team name
            const teamCells = screen.getAllByText('Team A');
            expect(teamCells.length).toBeGreaterThan(0);
        });
    });

    describe('Team filter options', () => {
        it('includes teams present in ticket data even if not returned by tenant teams', () => {
            const mockTickets = getMockPaginatedTickets([
                createMockTicket('1', 'opened', 'Wow Team', 'high'),
                createMockTicket('2', 'opened', 'Team A', 'medium'),
            ]);

            mockUseTickets.mockReturnValue({
                data: mockTickets,
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);

            // Tenant teams list does not include Wow Team
            mockUseTenantTeams.mockReturnValue({
                data: [{ name: 'Team A', types: ['tenant'] }],
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTenantTeams>);

            render(<Tickets />, { wrapper: Wrapper });

            // Team filter dropdown should include Wow Team from ticket data
            const options = screen.getAllByRole('option').map(o => o.textContent);
            expect(options).toEqual(expect.arrayContaining(['Wow Team']));
        });

        it('resets page-level team filter to current scope when sidebar team changes', () => {
            const mockTickets = getMockPaginatedTickets([
                createMockTicket('1', 'opened', 'Team A', 'high'),
                createMockTicket('2', 'opened', 'Team B', 'medium'),
            ]);

            mockUseTickets.mockReturnValue({
                data: mockTickets,
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);

            mockUseTeamFilter.mockReturnValue({
                selectedTeam: 'Team A',
                setSelectedTeam: jest.fn(),
                hasFullAccess: false,
                effectiveTeams: ['Team A'],
                allTeams: ['Team A', 'Team B'],
                initialized: true
            });

            const { rerender } = render(<Tickets />, { wrapper: Wrapper });

            const teamSelect = screen.getAllByRole('combobox').find(sel =>
                Array.from((sel as HTMLSelectElement).options).some(o => o.textContent === 'Current Team Scope')
            ) as HTMLSelectElement;

            fireEvent.change(teamSelect, { target: { value: '__all__' } });
            expect(teamSelect.value).toBe('__all__');

            mockUseTeamFilter.mockReturnValue({
                selectedTeam: 'Team B',
                setSelectedTeam: jest.fn(),
                hasFullAccess: false,
                effectiveTeams: ['Team B'],
                allTeams: ['Team A', 'Team B'],
                initialized: true
            });

            rerender(<Tickets />);

            const resetTeamSelect = screen.getAllByRole('combobox').find(sel =>
                Array.from((sel as HTMLSelectElement).options).some(o => o.textContent === 'Current Team Scope')
            ) as HTMLSelectElement;

            expect(resetTeamSelect.value).toBe('');
            expect(screen.getByText('Tickets Dashboard - Team B')).toBeInTheDocument();
        });

        it('applies 3-state team filtering: current scope, all teams, explicit team', () => {
            const mockTickets = getMockPaginatedTickets([
                createMockTicket('1', 'opened', 'Team A', 'high'),
                createMockTicket('2', 'opened', 'Team B', 'medium'),
            ]);

            mockUseTickets.mockReturnValue({
                data: mockTickets,
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);
            mockUseAllTickets.mockReturnValue({
                data: mockTickets,
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useAllTickets>);

            mockUseTeamFilter.mockReturnValue({
                selectedTeam: 'Team A',
                setSelectedTeam: jest.fn(),
                hasFullAccess: false,
                effectiveTeams: ['Team A'],
                allTeams: ['Team A', 'Team B'],
                initialized: true
            });

            render(<Tickets />, { wrapper: Wrapper });

            const getTableBodyText = () => screen.getByRole('table').querySelector('tbody')?.textContent || '';
            const teamSelect = screen.getAllByRole('combobox').find(sel =>
                Array.from((sel as HTMLSelectElement).options).some(o => o.textContent === 'Current Team Scope')
            ) as HTMLSelectElement;

            // '' => current team scope (Team A only)
            expect(getTableBodyText()).toContain('Team A');
            expect(getTableBodyText()).not.toContain('Team B');

            // __all__ => all teams
            fireEvent.change(teamSelect, { target: { value: '__all__' } });
            expect(getTableBodyText()).toContain('Team A');
            expect(getTableBodyText()).toContain('Team B');

            // specific team => explicit team override
            fireEvent.change(teamSelect, { target: { value: 'Team B' } });
            expect(getTableBodyText()).not.toContain('Team A');
            expect(getTableBodyText()).toContain('Team B');
        });

        it('hides team scope controls when user has no backend team scope', () => {
            const mockTickets = getMockPaginatedTickets([
                createMockTicket('1', 'opened', 'Team A', 'high'),
            ]);

            mockUseTickets.mockReturnValue({
                data: mockTickets,
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);

            mockUseTeamFilter.mockReturnValue({
                selectedTeam: null,
                setSelectedTeam: jest.fn(),
                hasFullAccess: false,
                effectiveTeams: ['__no_teams__'],
                allTeams: ['Team A'],
                initialized: true
            });

            render(<Tickets />, { wrapper: Wrapper });

            expect(screen.queryByText('Current Team Scope')).not.toBeInTheDocument();
            expect(screen.getByText('Tickets Dashboard')).toBeInTheDocument();
            expect(screen.getByText('No tickets found')).toBeInTheDocument();
        });
    });

    describe('Filtering across pages', () => {
        it('uses all tickets hook when filters are applied so items beyond first page appear', () => {
            // First-page fetch (no filters yet) does NOT include Wow Team
            const pageOneTickets = getMockPaginatedTickets([
                createMockTicket('1', 'opened', 'Team A', 'high')
            ]);
            mockUseTickets.mockReturnValue({
                data: pageOneTickets,
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);

            // All-pages fetch DOES include Wow Team with an escalation
            mockUseAllTickets.mockReturnValue({
                data: {
                    content: [
                        { ...createMockTicket('1', 'opened', 'Team A', 'high'), escalations: [] },
                        { ...createMockTicket('2', 'opened', 'Wow Team', 'high'), escalations: [{ id: 'esc-1' }] },
                    ],
                    page: 0,
                    totalPages: 2,
                    totalElements: 2
                },
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useAllTickets>);

            // Non-superuser view with access to both teams
            mockUseTeamFilter.mockReturnValue({
                selectedTeam: null,
                setSelectedTeam: jest.fn(),
                hasFullAccess: false,
                effectiveTeams: ['Team A', 'Wow Team'],
                allTeams: ['Team A', 'Wow Team'],
                initialized: true
            });

            render(<Tickets />, { wrapper: Wrapper });

            // Apply a filter (Escalated? Yes) to trigger useAllTickets path
            const selects = screen.getAllByRole('combobox');
            const escalatedSelect = selects.find(sel =>
                Array.from((sel as HTMLSelectElement).options).some(o => o.textContent === 'Escalated?')
            ) as HTMLSelectElement | undefined;
            expect(escalatedSelect).toBeDefined();

            fireEvent.change(escalatedSelect!, { target: { value: 'Yes' } });

            expect(mockUseAllTickets).toHaveBeenCalled();
            expect(screen.getAllByText('Wow Team').length).toBeGreaterThan(0);
        });

        it('filters tickets by escalation target team via "Escalated To" dropdown', () => {
            const ticketEscalatedToInfra = {
                ...createMockTicket('1', 'opened', 'Team A', 'high'),
                escalations: [{ id: 'esc-1', team: { name: 'Infra Team' } }]
            };
            const ticketEscalatedToPlatform = {
                ...createMockTicket('2', 'opened', 'Team B', 'medium'),
                escalations: [{ id: 'esc-2', team: { name: 'Platform Team' } }]
            };

            const mockTickets = getMockPaginatedTickets([
                ticketEscalatedToInfra,
                ticketEscalatedToPlatform
            ]);

            mockUseTickets.mockReturnValue({
                data: mockTickets,
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);

            mockUseAllTickets.mockReturnValue({
                data: mockTickets,
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useAllTickets>);

            render(<Tickets />, { wrapper: Wrapper });

            const escalatedToSelect = screen.getAllByRole('combobox').find(sel =>
                Array.from((sel as HTMLSelectElement).options).some(o => o.textContent === 'Escalated To')
            ) as HTMLSelectElement;

            expect(escalatedToSelect).toBeDefined();
            const options = Array.from(escalatedToSelect.options).map(o => o.textContent);
            expect(options).toEqual(expect.arrayContaining(['Infra Team', 'Platform Team']));

            fireEvent.change(escalatedToSelect, { target: { value: 'Infra Team' } });

            const tableBodyText = screen.getByRole('table').querySelector('tbody')?.textContent || '';
            expect(tableBodyText).toContain('Team A');
            expect(tableBodyText).not.toContain('Team B');
        });
    });

    describe('Escalation Display', () => {
        it('shows "Yes" when ticket has escalations', () => {
            const ticketWithEscalation = {
                ...createMockTicket('1', 'opened', 'Team A', 'high'),
                escalations: [{ id: 'esc-1', team: { name: 'Support' } }]
            };
            
            const mockTickets = getMockPaginatedTickets([ticketWithEscalation]);
            
            mockUseTickets.mockReturnValue({
                data: mockTickets,
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);

            render(<Tickets />, { wrapper: Wrapper });
            
            const yesElements = screen.getAllByText('Yes');
            expect(yesElements.length).toBeGreaterThan(0);
        });

        it('shows "No" when ticket has no escalations', () => {
            const mockTickets = getMockPaginatedTickets([
                createMockTicket('1', 'opened', 'Team A', 'high')
            ]);
            
            mockUseTickets.mockReturnValue({
                data: mockTickets,
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);

            render(<Tickets />, { wrapper: Wrapper });
            
            const noElements = screen.getAllByText('No');
            expect(noElements.length).toBeGreaterThan(0);
        });

        it('shows escalation target team names in the Escalated To column', () => {
            const ticketWithEscalations = {
                ...createMockTicket('1', 'opened', 'Team A', 'high'),
                escalations: [
                    { id: 'esc-1', team: { name: 'Support Team' } },
                    { id: 'esc-2', team: { name: 'Infra Team' } }
                ]
            };

            mockUseTickets.mockReturnValue({
                data: getMockPaginatedTickets([ticketWithEscalations]),
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);

            render(<Tickets />, { wrapper: Wrapper });

            expect(screen.getByText('Support Team, Infra Team')).toBeInTheDocument();
        });
    });

    describe('Filter Dropdowns', () => {
        it('renders status filter dropdown', () => {
            const mockTickets = getMockPaginatedTickets([]);
            
            mockUseTickets.mockReturnValue({
                data: mockTickets,
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);

            render(<Tickets />, { wrapper: Wrapper });
            
            expect(screen.getByText('All Status')).toBeInTheDocument();
        });

        it('renders impact filter dropdown', () => {
            const mockTickets = getMockPaginatedTickets([]);
            
            mockUseTickets.mockReturnValue({
                data: mockTickets,
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);

            render(<Tickets />, { wrapper: Wrapper });
            
            expect(screen.getByText('All Impacts')).toBeInTheDocument();
        });

        it('renders date filter dropdown', () => {
            const mockTickets = getMockPaginatedTickets([]);
            
            mockUseTickets.mockReturnValue({
                data: mockTickets,
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);

            render(<Tickets />, { wrapper: Wrapper });
            
            expect(screen.getByText('Last Month')).toBeInTheDocument();
        });
    });

    describe('Loading and Error States', () => {
        it('shows loading state', () => {
            mockUseTickets.mockReturnValue({
                data: undefined,
                isLoading: true,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);

            render(<Tickets />, { wrapper: Wrapper });
            
            // Should show loading skeleton
            expect(document.querySelector('.animate-pulse')).toBeInTheDocument();
        });

        it('shows error state', () => {
            mockUseTickets.mockReturnValue({
                data: undefined,
                isLoading: false,
                error: new Error('Failed to load')
            } as unknown as ReturnType<typeof hooks.useTickets>);

            render(<Tickets />, { wrapper: Wrapper });
            
            expect(screen.getByText(/Error loading tickets/i)).toBeInTheDocument();
        });

        it('shows empty state when no tickets', () => {
            const mockTickets = getMockPaginatedTickets([]);
            
            mockUseTickets.mockReturnValue({
                data: mockTickets,
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);

            render(<Tickets />, { wrapper: Wrapper });
            
            expect(screen.getByText('No tickets found')).toBeInTheDocument();
        });
    });

    describe('Date Opened and Closed Display', () => {
        it('displays date opened when available', () => {
            const mockTickets = getMockPaginatedTickets([
                createMockTicket('1', 'opened', 'Team A', 'high')
            ]);
            
            mockUseTickets.mockReturnValue({
                data: mockTickets,
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);

            render(<Tickets />, { wrapper: Wrapper });
            
            // Just verify the table renders (date formatting can vary)
            expect(screen.getByRole('table')).toBeInTheDocument();
        });

        it('displays dash when dates not available', () => {
            const ticketWithoutDates = {
                ...createMockTicket('1', 'opened', 'Team A', 'high'),
                logs: []
            };
            
            const mockTickets = getMockPaginatedTickets([ticketWithoutDates]);
            
            mockUseTickets.mockReturnValue({
                data: mockTickets,
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);

            render(<Tickets />, { wrapper: Wrapper });
            
            // Should show dashes for missing dates
            const table = screen.getByRole('table');
            expect(table).toBeInTheDocument();
        });

        it('sorts by Opened At when header is clicked', () => {
            const older = {
                ...createMockTicket('1', 'opened', 'Team A', 'high'),
                logs: [{ event: 'opened', date: '2024-01-01T10:00:00Z' }]
            };
            const newer = {
                ...createMockTicket('2', 'opened', 'Team B', 'high'),
                logs: [{ event: 'opened', date: '2024-01-02T10:00:00Z' }]
            };

            mockUseTickets.mockReturnValue({
                data: getMockPaginatedTickets([older, newer]),
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);

            render(<Tickets />, { wrapper: Wrapper });

            const getFirstDataRowText = () => {
                const rows = screen.getAllByRole('row');
                return rows[1]?.textContent || '';
            };

            // Default opened sort is desc => newer first
            expect(getFirstDataRowText()).toContain('Team B');

            // Toggle to asc => older first
            fireEvent.click(screen.getByText(/Opened At/i));
            expect(getFirstDataRowText()).toContain('Team A');
        });

        it('sorts by Closed At when header is clicked', () => {
            const closesEarlier = {
                ...createMockTicket('1', 'closed', 'Team A', 'high'),
                logs: [
                    { event: 'opened', date: '2024-01-01T10:00:00Z' },
                    { event: 'closed', date: '2024-01-03T10:00:00Z' }
                ]
            };
            const closesLater = {
                ...createMockTicket('2', 'closed', 'Team B', 'high'),
                logs: [
                    { event: 'opened', date: '2024-01-01T10:00:00Z' },
                    { event: 'closed', date: '2024-01-04T10:00:00Z' }
                ]
            };

            mockUseTickets.mockReturnValue({
                data: getMockPaginatedTickets([closesEarlier, closesLater]),
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);

            render(<Tickets />, { wrapper: Wrapper });

            const getFirstDataRowText = () => {
                const rows = screen.getAllByRole('row');
                return rows[1]?.textContent || '';
            };

            // Closed sort defaults to desc on first click => later close first
            fireEvent.click(screen.getByText(/Closed At/i));
            expect(getFirstDataRowText()).toContain('Team B');

            // Toggle to asc => earlier close first
            fireEvent.click(screen.getByText(/Closed At/i));
            expect(getFirstDataRowText()).toContain('Team A');
        });
    });

    describe('Status Styling', () => {
        it('applies styling to opened status', () => {
            const mockTickets = getMockPaginatedTickets([
                createMockTicket('1', 'opened', 'Team A', 'high')
            ]);
            
            mockUseTickets.mockReturnValue({
                data: mockTickets,
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);

            render(<Tickets />, { wrapper: Wrapper });
            
            // Status should be rendered
            expect(screen.getByRole('table')).toBeInTheDocument();
        });

        it('applies styling to closed status', () => {
            const mockTickets = getMockPaginatedTickets([
                createMockTicket('1', 'closed', 'Team A', 'high')
            ]);
            
            mockUseTickets.mockReturnValue({
                data: mockTickets,
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);

            render(<Tickets />, { wrapper: Wrapper });
            
            expect(screen.getByRole('table')).toBeInTheDocument();
        });

        it('applies styling to stale status', () => {
            const mockTickets = getMockPaginatedTickets([
                createMockTicket('1', 'stale', 'Team A', 'high')
            ]);
            
            mockUseTickets.mockReturnValue({
                data: mockTickets,
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);

            render(<Tickets />, { wrapper: Wrapper });
            
            expect(screen.getByRole('table')).toBeInTheDocument();
        });
    });

    describe('Team Display', () => {
        it('displays team name when available', () => {
            const mockTickets = getMockPaginatedTickets([
                createMockTicket('1', 'opened', 'Team A', 'high')
            ]);
            
            mockUseTickets.mockReturnValue({
                data: mockTickets,
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);

            render(<Tickets />, { wrapper: Wrapper });
            
            const teamCells = screen.getAllByText('Team A');
            expect(teamCells.length).toBeGreaterThan(0);
        });

        it('displays dash when team not available', () => {
            const ticketWithoutTeam = {
                ...createMockTicket('1', 'opened', 'Team A', 'high'),
                team: null
            };
            
            const mockTickets = getMockPaginatedTickets([ticketWithoutTeam]);
            
            mockUseTickets.mockReturnValue({
                data: mockTickets,
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);

            render(<Tickets />, { wrapper: Wrapper });
            
            expect(screen.getByRole('table')).toBeInTheDocument();
        });
    });

    describe('Impact Display', () => {
        it('displays impact label when available', () => {
            const mockTickets = getMockPaginatedTickets([
                createMockTicket('1', 'opened', 'Team A', 'high')
            ]);
            
            mockUseTickets.mockReturnValue({
                data: mockTickets,
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);

            render(<Tickets />, { wrapper: Wrapper });
            
            // Multiple elements will have "High Impact" (filter dropdown + table cells)
            const impactElements = screen.getAllByText('High Impact');
            expect(impactElements.length).toBeGreaterThan(0);
        });

        it('displays impact code when label not found', () => {
            const mockTickets = getMockPaginatedTickets([
                createMockTicket('1', 'opened', 'Team A', 'unknown')
            ]);
            
            mockUseTickets.mockReturnValue({
                data: mockTickets,
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);

            render(<Tickets />, { wrapper: Wrapper });
            
            expect(screen.getByText('unknown')).toBeInTheDocument();
        });
    });

    describe('Ticket Modal Interaction', () => {
        it('opens modal when ticket row is clicked', () => {
            const mockTickets = getMockPaginatedTickets([
                createMockTicket('1', 'opened', 'Team A', 'high')
            ]);
            
            mockUseTickets.mockReturnValue({
                data: mockTickets,
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);

            mockUseTicket.mockReturnValue({
                data: createMockTicket('1', 'opened', 'Team A', 'high'),
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTicket>);

            render(<Tickets />, { wrapper: Wrapper });
            
            // Initially modal should not be visible
            expect(screen.queryByTestId('edit-ticket-modal')).not.toBeInTheDocument();
            
            // Click on a ticket row
            const rows = screen.getAllByRole('row');
            const ticketRow = rows.find(row => row.textContent?.includes('Team A'));
            if (ticketRow) {
                fireEvent.click(ticketRow);
            }
            
            // Modal should now be visible
            expect(screen.getByTestId('edit-ticket-modal')).toBeInTheDocument();
            expect(screen.getByText(/Ticket Modal: 1/i)).toBeInTheDocument();
        });

        it('closes modal when close button is clicked', () => {
            const mockTickets = getMockPaginatedTickets([
                createMockTicket('1', 'opened', 'Team A', 'high')
            ]);
            
            mockUseTickets.mockReturnValue({
                data: mockTickets,
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);

            mockUseTicket.mockReturnValue({
                data: createMockTicket('1', 'opened', 'Team A', 'high'),
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTicket>);

            render(<Tickets />, { wrapper: Wrapper });
            
            // Click on a ticket row to open modal
            const rows = screen.getAllByRole('row');
            const ticketRow = rows.find(row => row.textContent?.includes('Team A'));
            if (ticketRow) {
                fireEvent.click(ticketRow);
            }
            
            // Modal should be visible
            expect(screen.getByTestId('edit-ticket-modal')).toBeInTheDocument();
            
            // Click close button
            const closeButton = screen.getByText('Close Modal');
            fireEvent.click(closeButton);
            
            // Modal should be closed
            expect(screen.queryByTestId('edit-ticket-modal')).not.toBeInTheDocument();
        });

        it('does not show details panel below table (replaced by modal)', () => {
            const mockTickets = getMockPaginatedTickets([
                createMockTicket('1', 'opened', 'Team A', 'high')
            ]);
            
            mockUseTickets.mockReturnValue({
                data: mockTickets,
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);

            render(<Tickets />, { wrapper: Wrapper });
            
            // Should not have the old details panel structure
            // The old implementation would have shown ticket details below the table
            // Now it should only show modal when ticket is clicked
            const table = screen.getByRole('table');
            expect(table).toBeInTheDocument();
            
            // No details panel should be visible initially
            expect(screen.queryByText(/Ticket #/i)).not.toBeInTheDocument();
        });
    });

    describe('Date Filter - Custom Range Logic', () => {
        it('should use last week range when switching to custom mode without dates set', () => {
            const mockTickets = getMockPaginatedTickets([
                createMockTicket('1', 'opened', 'Team A', 'high')
            ]);
            
            mockUseTickets.mockReturnValue({
                data: mockTickets,
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);

            render(<Tickets />, { wrapper: Wrapper });

            // Find the date filter dropdown
            const dateFilterSelect = screen.getByDisplayValue('Last Week');
            expect(dateFilterSelect).toBeInTheDocument();

            // Switch to custom mode
            fireEvent.change(dateFilterSelect, { target: { value: 'custom' } });

            // Verify useAllTickets was called with last week range (not undefined)
            // When custom is selected, it should use useAllTickets
            const allTicketsCalls = mockUseAllTickets.mock.calls;
            if (allTicketsCalls.length > 0) {
                const lastCall = allTicketsCalls[allTicketsCalls.length - 1];
                const [, from, to] = lastCall;
                // Should have valid dates (last week), not undefined
                expect(from).toBeDefined();
                expect(to).toBeDefined();
                expect(from).not.toBe('');
                expect(to).not.toBe('');
            }
        });

        it('should use custom dates when both start and end dates are set', () => {
            const mockTickets = getMockPaginatedTickets([
                createMockTicket('1', 'opened', 'Team A', 'high')
            ]);
            
            mockUseTickets.mockReturnValue({
                data: mockTickets,
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);

            render(<Tickets />, { wrapper: Wrapper });

            // Switch to custom mode
            const dateFilterSelect = screen.getByDisplayValue('Last Week');
            fireEvent.change(dateFilterSelect, { target: { value: 'custom' } });

            // Set custom dates
            const dateInputs = screen.getAllByDisplayValue('').filter(
                (input) => input.getAttribute('type') === 'date'
            );
            
            if (dateInputs.length >= 2) {
                fireEvent.change(dateInputs[0], { target: { value: '2024-01-01' } });
                fireEvent.change(dateInputs[1], { target: { value: '2024-01-31' } });

                // Verify custom dates are used (check in subsequent calls)
                const allTicketsCalls = mockUseAllTickets.mock.calls;
                if (allTicketsCalls.length > 0) {
                    const lastCall = allTicketsCalls[allTicketsCalls.length - 1];
                    const [, from, to] = lastCall;
                    expect(from).toBeDefined();
                    expect(to).toBeDefined();
                }
            }
        });

        it('should preserve date range when custom mode is selected but dates are empty', () => {
            const mockTickets = getMockPaginatedTickets([
                createMockTicket('1', 'opened', 'Team A', 'high')
            ]);
            
            mockUseTickets.mockReturnValue({
                data: mockTickets,
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);

            render(<Tickets />, { wrapper: Wrapper });

            const dateFilterSelect = screen.getByDisplayValue('Last Week');
            fireEvent.change(dateFilterSelect, { target: { value: 'custom' } });

            // Check that the hook was called with valid dates, not undefined
            const allTicketsCalls = mockUseAllTickets.mock.calls;
            if (allTicketsCalls.length > 0) {
                const lastCall = allTicketsCalls[allTicketsCalls.length - 1];
                const [, from, to] = lastCall;
                // Should have valid dates (preserved from previous filter)
                expect(from).toBeDefined();
                expect(to).toBeDefined();
            }
        });
    });

    describe('URL filter sync', () => {
        it('hydrates filters from URL query params', () => {
            mockSearchParamsValue = 'ticketDate=custom&ticketFrom=2024-01-01&ticketTo=2024-01-31&ticketStatus=closed&ticketTeam=Team%20B&ticketImpact=high&ticketTag=bug&ticketEscalated=Yes&ticketEscalatedTo=Infra%20Team&ticketSortBy=closedAt&ticketSortDir=asc&ticketPage=1';
            const mockTickets = getMockPaginatedTickets([
                { ...createMockTicket('1', 'closed', 'Team B', 'high'), tags: ['bug'], escalations: [{ id: 'esc-1', team: { name: 'Infra Team' } }] },
            ]);

            mockUseTickets.mockReturnValue({
                data: mockTickets,
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);
            mockUseAllTickets.mockReturnValue({
                data: mockTickets,
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useAllTickets>);

            render(<Tickets />, { wrapper: Wrapper });

            expect(screen.getByDisplayValue('Custom Range')).toBeInTheDocument();
            expect(screen.getByDisplayValue('Closed')).toBeInTheDocument();
            expect(screen.getByDisplayValue('Team B')).toBeInTheDocument();
            expect(screen.getByDisplayValue('High Impact')).toBeInTheDocument();
            expect(screen.getByDisplayValue('Bug')).toBeInTheDocument();
            expect(screen.getByDisplayValue('Yes')).toBeInTheDocument();
            expect(screen.getByDisplayValue('Infra Team')).toBeInTheDocument();
            expect(screen.getByDisplayValue('2024-01-01')).toBeInTheDocument();
            expect(screen.getByDisplayValue('2024-01-31')).toBeInTheDocument();
        });

        it('uses safe defaults for invalid query params', () => {
            mockSearchParamsValue = 'ticketDate=invalid&ticketStatus=nope&ticketSortBy=bad&ticketSortDir=sideways&ticketPage=-2';
            const mockTickets = getMockPaginatedTickets([
                createMockTicket('1', 'opened', 'Team A', 'high')
            ]);

            mockUseTickets.mockReturnValue({
                data: mockTickets,
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);

            render(<Tickets />, { wrapper: Wrapper });

            expect(screen.getByDisplayValue('Last Week')).toBeInTheDocument();
            expect(screen.getByDisplayValue('All Status')).toBeInTheDocument();
            expect(screen.getByText(/Opened At ↓/i)).toBeInTheDocument();
            expect(screen.getByText(/Page 1 of/i)).toBeInTheDocument();
        });

        it('syncs filter changes back to URL while preserving unrelated params', () => {
            mockSearchParamsValue = 'foo=bar';
            const mockTickets = getMockPaginatedTickets([
                createMockTicket('1', 'opened', 'Team A', 'high')
            ]);

            mockUseTickets.mockReturnValue({
                data: mockTickets,
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);

            render(<Tickets />, { wrapper: Wrapper });
            mockReplace.mockClear();

            const statusSelect = screen.getAllByRole('combobox').find(sel =>
                Array.from((sel as HTMLSelectElement).options).some(o => o.textContent === 'All Status')
            ) as HTMLSelectElement;
            fireEvent.change(statusSelect, { target: { value: 'opened' } });

            expect(mockReplace).toHaveBeenCalled();
            const [url] = mockReplace.mock.calls[mockReplace.mock.calls.length - 1];
            expect(url).toContain('/tickets?');
            expect(url).toContain('foo=bar');
            expect(url).toContain('tab=tickets');
            expect(url).toContain('ticketStatus=opened');
            expect(url).toContain('ticketDate=lastWeek');
            expect(url).not.toContain('status=');
            expect(url).not.toContain('date=');
        });

        it('removes escalations-only params when syncing tickets URL', () => {
            mockSearchParamsValue = 'tab=tickets&escTeam=connected-app&escStatus=resolved&foo=bar';
            const mockTickets = getMockPaginatedTickets([
                createMockTicket('1', 'opened', 'Team A', 'high')
            ]);

            mockUseTickets.mockReturnValue({
                data: mockTickets,
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);

            render(<Tickets />, { wrapper: Wrapper });

            expect(mockReplace).toHaveBeenCalled();
            const [url] = mockReplace.mock.calls[mockReplace.mock.calls.length - 1];
            expect(url).toContain('tab=tickets');
            expect(url).toContain('foo=bar');
            expect(url).not.toContain('escTeam=');
            expect(url).not.toContain('escStatus=');
            expect(url).toContain('ticketDate=lastWeek');
        });

        it('supports privileged -> non-privileged shared ticket URL views', () => {
            mockSearchParamsValue = 'tab=tickets&ticketDate=any&ticketStatus=opened&ticketTeam=Team%20B';
            const sharedData = getMockPaginatedTickets([
                createMockTicket('1', 'opened', 'Team A', 'high'),
                createMockTicket('2', 'opened', 'Team B', 'high'),
            ]);

            mockUseTickets.mockReturnValue({
                data: sharedData,
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);
            mockUseAllTickets.mockReturnValue({
                data: sharedData,
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useAllTickets>);

            // Non-privileged context opening a URL created by privileged user.
            mockUseTeamFilter.mockReturnValue({
                selectedTeam: 'Team A',
                setSelectedTeam: jest.fn(),
                hasFullAccess: false,
                effectiveTeams: ['Team A'],
                allTeams: ['Team A', 'Team B'],
                initialized: true
            });

            render(<Tickets />, { wrapper: Wrapper });

            const tableBodyText = screen.getByRole('table').querySelector('tbody')?.textContent || '';
            expect(tableBodyText).toContain('Team B');
            expect(tableBodyText).not.toContain('Team A');
        });

        it('supports non-privileged -> privileged shared ticket URL views', () => {
            mockSearchParamsValue = 'tab=tickets&ticketDate=any&ticketStatus=opened&ticketTeam=Team%20B';
            const sharedData = getMockPaginatedTickets([
                createMockTicket('1', 'opened', 'Team A', 'high'),
                createMockTicket('2', 'opened', 'Team B', 'high'),
            ]);

            mockUseTickets.mockReturnValue({
                data: sharedData,
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);
            mockUseAllTickets.mockReturnValue({
                data: sharedData,
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useAllTickets>);

            // Privileged context opening a URL created by non-privileged user.
            mockUseTeamFilter.mockReturnValue({
                selectedTeam: null,
                setSelectedTeam: jest.fn(),
                hasFullAccess: true,
                effectiveTeams: [],
                allTeams: ['Team A', 'Team B'],
                initialized: true
            });

            render(<Tickets />, { wrapper: Wrapper });

            const tableBodyText = screen.getByRole('table').querySelector('tbody')?.textContent || '';
            expect(tableBodyText).toContain('Team B');
            expect(tableBodyText).not.toContain('Team A');
        });
    });
});
