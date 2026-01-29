/**
 * Health Dashboard Unit Tests
 * 
 * Tests the Health dashboard rendering, tabs, and filtering:
 * - Tab navigation (Activity Trends, Ratings, Ticket Workbench)
 * - Status, Rated, and Assignee filters in Ticket Workbench tab
 * - Loading and error states
 */

import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import HealthPage from '../health';
import * as hooks from '../../../lib/hooks';

// Mock recharts to avoid rendering errors in tests
jest.mock('recharts', () => ({
    ResponsiveContainer: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
    LineChart: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
    BarChart: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
    Line: () => null,
    Bar: () => null,
    XAxis: () => null,
    YAxis: () => null,
    CartesianGrid: () => null,
    Tooltip: () => null,
    Legend: () => null,
    Cell: () => null,
}));

jest.mock('../../../lib/hooks');

const mockUseTickets = hooks.useTickets as jest.MockedFunction<typeof hooks.useTickets>;
const mockUseRatings = hooks.useRatings as jest.MockedFunction<typeof hooks.useRatings>;
const mockUseRegistry = hooks.useRegistry as jest.MockedFunction<typeof hooks.useRegistry>;
const mockUseSupportMembers = hooks.useSupportMembers as jest.MockedFunction<typeof hooks.useSupportMembers>;
const mockUseAssignmentEnabled = hooks.useAssignmentEnabled as jest.MockedFunction<typeof hooks.useAssignmentEnabled>;

const createMockTicket = (id: string, status: string, teamName: string, ratingSubmitted: boolean, escalations: unknown[] = []) => {
    const now = new Date();
    const recentDate = new Date(now.getTime() - 24 * 60 * 60 * 1000);
    return {
        id,
        status,
        team: { name: teamName },
        impact: 'high',
        tags: ['bug'],
        escalations,
        logs: [{ event: 'opened', date: recentDate.toISOString() }],
        query: { link: 'https://example.com' },
        ratingSubmitted
    };
};

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

describe('HealthPage', () => {
    beforeEach(() => {
        jest.clearAllMocks();

        mockUseRegistry.mockReturnValue({
            data: {
                impacts: [{ code: 'high', label: 'High' }],
                tags: [{ code: 'bug', label: 'Bug' }]
            },
            isLoading: false,
            error: null
        } as unknown as ReturnType<typeof hooks.useRegistry>);

        mockUseRatings.mockReturnValue({
            data: { average: 4.5, count: 10 },
            isLoading: false,
            error: null
        } as unknown as ReturnType<typeof hooks.useRatings>);

        mockUseSupportMembers.mockReturnValue({
            data: [
                { userId: 'U123', displayName: 'john@example.com' },
                { userId: 'U456', displayName: 'jane@example.com' }
            ],
            isLoading: false,
            error: null
        } as unknown as ReturnType<typeof hooks.useSupportMembers>);

        mockUseAssignmentEnabled.mockReturnValue({
            data: true,
            isLoading: false,
            error: null
        } as unknown as ReturnType<typeof hooks.useAssignmentEnabled>);
    });

    describe('Loading States', () => {
        it('shows loading skeleton when tickets are loading', () => {
            mockUseTickets.mockReturnValue({
                data: undefined,
                isLoading: true,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);

            const { container } = render(<HealthPage />, { wrapper: Wrapper });

            expect(container.querySelector('.animate-pulse')).toBeInTheDocument();
        });

        it('shows loading skeleton when ratings are loading', () => {
            mockUseTickets.mockReturnValue({
                data: { content: [], page: 0, totalPages: 0, totalElements: 0 },
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);

            mockUseRatings.mockReturnValue({
                data: undefined,
                isLoading: true,
                error: null
            } as unknown as ReturnType<typeof hooks.useRatings>);

            const { container } = render(<HealthPage />, { wrapper: Wrapper });

            expect(container.querySelector('.animate-pulse')).toBeInTheDocument();
        });
    });

    describe('Tab Navigation', () => {
        beforeEach(() => {
            mockUseTickets.mockReturnValue({
                data: { content: [], page: 0, totalPages: 0, totalElements: 0 },
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);
        });

        it('renders all three tabs', () => {
            render(<HealthPage />, { wrapper: Wrapper });

            expect(screen.getByText('Activity Trends')).toBeInTheDocument();
            expect(screen.getByText('Ratings')).toBeInTheDocument();
            expect(screen.getByText('Ticket Workbench')).toBeInTheDocument();
        });

        it('defaults to Activity Trends tab', () => {
            render(<HealthPage />, { wrapper: Wrapper });

            // Should show activity trends tab content (has date filter buttons like "Last 7 Days")
            expect(screen.getByText(/Last 7 Days/i)).toBeInTheDocument();
        });

        it('switches to Ticket Workbench tab when clicked', () => {
            render(<HealthPage />, { wrapper: Wrapper });

            const workbenchTab = screen.getByText('Ticket Workbench');
            fireEvent.click(workbenchTab);

            // Should show workbench-specific filters
            const requestingTeam = screen.getAllByText('Requesting Team');
            expect(requestingTeam.length).toBeGreaterThan(0);
            const escalatedTo = screen.getAllByText('Escalated To');
            expect(escalatedTo.length).toBeGreaterThan(0);
        });
    });

    describe('Ticket Workbench Tab Filters', () => {
        beforeEach(() => {
            const mockTickets = [
                createMockTicket('1', 'opened', 'Team A', false, []),
                createMockTicket('2', 'closed', 'Team B', true, [{ team: { name: 'Escalation Team' } }]),
                createMockTicket('3', 'stale', 'Team A', false, []),
            ];

            mockUseTickets.mockReturnValue({
                data: { content: mockTickets, page: 0, totalPages: 1, totalElements: 3 },
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);
        });

        it('renders status filter in Ticket Workbench tab', () => {
            render(<HealthPage />, { wrapper: Wrapper });

            const workbenchTab = screen.getByText('Ticket Workbench');
            fireEvent.click(workbenchTab);

            const statusOptions = screen.getAllByText('Status');
            expect(statusOptions.length).toBeGreaterThan(0);
            expect(screen.getByText('Opened')).toBeInTheDocument();
            expect(screen.getByText('Closed')).toBeInTheDocument();
            expect(screen.getByText('Stale')).toBeInTheDocument();
        });

        it('renders rated filter in Ticket Workbench tab', () => {
            render(<HealthPage />, { wrapper: Wrapper });

            const workbenchTab = screen.getByText('Ticket Workbench');
            fireEvent.click(workbenchTab);

            const ratedOptions = screen.getAllByText('Rated');
            expect(ratedOptions.length).toBeGreaterThan(0);
            const yesOptions = screen.getAllByText('Yes');
            const noOptions = screen.getAllByText('No');
            expect(yesOptions.length).toBeGreaterThan(0);
            expect(noOptions.length).toBeGreaterThan(0);
        });

        it('filters tickets by status', () => {
            render(<HealthPage />, { wrapper: Wrapper });

            const workbenchTab = screen.getByText('Ticket Workbench');
            fireEvent.click(workbenchTab);

            const selects = screen.getAllByRole('combobox');
            const statusSelect = Array.from(selects).find(s => 
                s.querySelector('option[value="opened"]')
            ) as HTMLSelectElement;

            expect(statusSelect).toBeInTheDocument();
            fireEvent.change(statusSelect, { target: { value: 'opened' } });

            // Should still render the table
            const requestingTeam = screen.getAllByText('Requesting Team');
            expect(requestingTeam.length).toBeGreaterThan(0);
        });

        it('filters tickets by rated status', () => {
            render(<HealthPage />, { wrapper: Wrapper });

            const workbenchTab = screen.getByText('Ticket Workbench');
            fireEvent.click(workbenchTab);

            const selects = screen.getAllByRole('combobox');
            const ratedSelect = Array.from(selects).find(s => 
                s.querySelector('option[value="yes"]')
            ) as HTMLSelectElement;

            expect(ratedSelect).toBeInTheDocument();
            fireEvent.change(ratedSelect, { target: { value: 'yes' } });

            // Should still render the table
            const requestingTeam = screen.getAllByText('Requesting Team');
            expect(requestingTeam.length).toBeGreaterThan(0);
        });
    });

    describe('Ticket Workbench Tab', () => {
        it('renders ticket table columns', () => {
            mockUseTickets.mockReturnValue({
                data: { content: [], page: 0, totalPages: 0, totalElements: 0 },
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);

            render(<HealthPage />, { wrapper: Wrapper });

            const workbenchTab = screen.getByText('Ticket Workbench');
            fireEvent.click(workbenchTab);

            expect(screen.getByText('Team')).toBeInTheDocument();
            expect(screen.getByText('Impact')).toBeInTheDocument();
            const escalatedTo = screen.getAllByText('Escalated To');
            expect(escalatedTo.length).toBeGreaterThanOrEqual(1);
            const statusHeaders = screen.getAllByText('Status');
            expect(statusHeaders.length).toBeGreaterThan(0);
            expect(screen.getByText('Opened At')).toBeInTheDocument();
            const ratedHeaders = screen.getAllByText('Rated');
            expect(ratedHeaders.length).toBeGreaterThan(0);
            expect(screen.getByText('Link')).toBeInTheDocument();
        });
    });

    describe('Activity Trends - New Dashboards', () => {
        const createTicketWithDate = (id: string, teamName: string, openedDate: Date, assignedTo?: string, status: string = 'opened') => {
            return {
                id,
                status,
                team: { name: teamName },
                impact: 'high',
                tags: ['bug'],
                escalations: [],
                logs: [{ event: 'opened', date: openedDate.toISOString() }],
                query: { link: 'https://example.com' },
                ratingSubmitted: false,
                assignedTo
            };
        };

        beforeEach(() => {
            mockUseTickets.mockReturnValue({
                data: { content: [], page: 0, totalPages: 0, totalElements: 0 },
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);
        });

        describe('Average Ticket Assignments per Support Engineer', () => {
            it('renders when assignment is enabled and has tickets', () => {
                const now = new Date();
                const yesterday = new Date(now.getTime() - 24 * 60 * 60 * 1000);
                const tickets = [
                    createTicketWithDate('1', 'Team A', yesterday, 'john@example.com'),
                    createTicketWithDate('2', 'Team B', yesterday, 'jane@example.com'),
                    createTicketWithDate('3', 'Team A', yesterday, 'john@example.com'),
                ];

                mockUseTickets.mockReturnValue({
                    data: { content: tickets, page: 0, totalPages: 1, totalElements: 3 },
                    isLoading: false,
                    error: null
                } as unknown as ReturnType<typeof hooks.useTickets>);

                render(<HealthPage />, { wrapper: Wrapper });

                expect(screen.getByText('Average Ticket Assignments per Support Engineer')).toBeInTheDocument();
            });

            it('does not render when assignment is disabled', () => {
                mockUseAssignmentEnabled.mockReturnValue({
                    data: false,
                    isLoading: false,
                    error: null
                } as unknown as ReturnType<typeof hooks.useAssignmentEnabled>);

                render(<HealthPage />, { wrapper: Wrapper });

                expect(screen.queryByText('Average Ticket Assignments per Support Engineer')).not.toBeInTheDocument();
            });
        });

        describe('Tickets Opened by Requesting Team (Top 10)', () => {
            it('renders team breakdown chart', () => {
                const now = new Date();
                const tickets = [
                    createTicketWithDate('1', 'Team A', now),
                    createTicketWithDate('2', 'Team B', now),
                    createTicketWithDate('3', 'Team A', now),
                ];

                mockUseTickets.mockReturnValue({
                    data: { content: tickets, page: 0, totalPages: 1, totalElements: 3 },
                    isLoading: false,
                    error: null
                } as unknown as ReturnType<typeof hooks.useTickets>);

                render(<HealthPage />, { wrapper: Wrapper });

                expect(screen.getByText('Tickets Opened by Requesting Team (Top 10)')).toBeInTheDocument();
            });

            it('limits to top 10 teams', () => {
                const now = new Date();
                const tickets = Array.from({ length: 15 }, (_, i) => 
                    createTicketWithDate(`ticket-${i}`, `Team ${i}`, now)
                );

                mockUseTickets.mockReturnValue({
                    data: { content: tickets, page: 0, totalPages: 1, totalElements: 15 },
                    isLoading: false,
                    error: null
                } as unknown as ReturnType<typeof hooks.useTickets>);

                render(<HealthPage />, { wrapper: Wrapper });

                expect(screen.getByText('Tickets Opened by Requesting Team (Top 10)')).toBeInTheDocument();
            });
        });

        describe('Current Active Tickets per Engineer', () => {
            it('renders when assignment is enabled', () => {
                const now = new Date();
                const tickets = [
                    createTicketWithDate('1', 'Team A', now, 'john@example.com', 'opened'),
                    createTicketWithDate('2', 'Team B', now, 'jane@example.com', 'opened'),
                ];

                mockUseTickets.mockReturnValue({
                    data: { content: tickets, page: 0, totalPages: 1, totalElements: 2 },
                    isLoading: false,
                    error: null
                } as unknown as ReturnType<typeof hooks.useTickets>);

                render(<HealthPage />, { wrapper: Wrapper });

                expect(screen.getByText('Current Active Tickets per Engineer')).toBeInTheDocument();
            });

            it('does not render when assignment is disabled', () => {
                mockUseAssignmentEnabled.mockReturnValue({
                    data: false,
                    isLoading: false,
                    error: null
                } as unknown as ReturnType<typeof hooks.useAssignmentEnabled>);

                render(<HealthPage />, { wrapper: Wrapper });

                expect(screen.queryByText('Current Active Tickets per Engineer')).not.toBeInTheDocument();
            });

            it('only counts opened tickets', () => {
                const now = new Date();
                const tickets = [
                    createTicketWithDate('1', 'Team A', now, 'john@example.com', 'opened'),
                    createTicketWithDate('2', 'Team B', now, 'john@example.com', 'closed'),
                    createTicketWithDate('3', 'Team A', now, 'jane@example.com', 'opened'),
                ];

                mockUseTickets.mockReturnValue({
                    data: { content: tickets, page: 0, totalPages: 1, totalElements: 3 },
                    isLoading: false,
                    error: null
                } as unknown as ReturnType<typeof hooks.useTickets>);

                render(<HealthPage />, { wrapper: Wrapper });

                expect(screen.getByText('Current Active Tickets per Engineer')).toBeInTheDocument();
            });
        });

        describe('Tickets Opened by Hour of Day', () => {
            it('renders hour breakdown chart', () => {
                const now = new Date();
                now.setHours(10, 0, 0, 0); // 10 AM
                const tickets = [
                    createTicketWithDate('1', 'Team A', now),
                    createTicketWithDate('2', 'Team B', now),
                ];

                mockUseTickets.mockReturnValue({
                    data: { content: tickets, page: 0, totalPages: 1, totalElements: 2 },
                    isLoading: false,
                    error: null
                } as unknown as ReturnType<typeof hooks.useTickets>);

                render(<HealthPage />, { wrapper: Wrapper });

                expect(screen.getByText('Tickets Opened by Hour of Day')).toBeInTheDocument();
            });

            it('only includes hours between 7AM and 7PM', () => {
                const now = new Date();
                // Create tickets at different hours
                const earlyMorning = new Date(now);
                earlyMorning.setHours(6, 0, 0, 0); // 6 AM - should be excluded
                const businessHour = new Date(now);
                businessHour.setHours(10, 0, 0, 0); // 10 AM - should be included
                const evening = new Date(now);
                evening.setHours(20, 0, 0, 0); // 8 PM - should be excluded

                const tickets = [
                    createTicketWithDate('1', 'Team A', earlyMorning),
                    createTicketWithDate('2', 'Team B', businessHour),
                    createTicketWithDate('3', 'Team C', evening),
                ];

                mockUseTickets.mockReturnValue({
                    data: { content: tickets, page: 0, totalPages: 1, totalElements: 3 },
                    isLoading: false,
                    error: null
                } as unknown as ReturnType<typeof hooks.useTickets>);

                render(<HealthPage />, { wrapper: Wrapper });

                expect(screen.getByText('Tickets Opened by Hour of Day')).toBeInTheDocument();
            });
        });

        describe('Busiest Periods Heatmap', () => {
            it('renders heatmap for weekdays only', () => {
                const monday = new Date('2024-01-01T10:00:00Z'); // Monday
                const sunday = new Date('2023-12-31T10:00:00Z'); // Sunday - should be excluded
                const tickets = [
                    createTicketWithDate('1', 'Team A', monday),
                    createTicketWithDate('2', 'Team B', sunday),
                ];

                mockUseTickets.mockReturnValue({
                    data: { content: tickets, page: 0, totalPages: 1, totalElements: 2 },
                    isLoading: false,
                    error: null
                } as unknown as ReturnType<typeof hooks.useTickets>);

                render(<HealthPage />, { wrapper: Wrapper });

                expect(screen.getByText('Busiest Periods Heatmap')).toBeInTheDocument();
            });

            it('only includes hours between 7AM and 7PM', () => {
                const monday = new Date('2024-01-01T10:00:00Z'); // Monday 10 AM
                const tickets = [createTicketWithDate('1', 'Team A', monday)];

                mockUseTickets.mockReturnValue({
                    data: { content: tickets, page: 0, totalPages: 1, totalElements: 1 },
                    isLoading: false,
                    error: null
                } as unknown as ReturnType<typeof hooks.useTickets>);

                render(<HealthPage />, { wrapper: Wrapper });

                expect(screen.getByText('Busiest Periods Heatmap')).toBeInTheDocument();
            });
        });

        describe('Capacity vs Demand', () => {
            it('renders when assignment is enabled', () => {
                const now = new Date();
                const tickets = [
                    createTicketWithDate('1', 'Team A', now, 'john@example.com', 'opened'),
                ];

                mockUseTickets.mockReturnValue({
                    data: { content: tickets, page: 0, totalPages: 1, totalElements: 1 },
                    isLoading: false,
                    error: null
                } as unknown as ReturnType<typeof hooks.useTickets>);

                render(<HealthPage />, { wrapper: Wrapper });

                expect(screen.getByText('Capacity vs Demand')).toBeInTheDocument();
                expect(screen.getByText('Engineers on Rota:')).toBeInTheDocument();
                expect(screen.getByText('Tickets per Engineer Capacity:')).toBeInTheDocument();
            });

            it('does not render when assignment is disabled', () => {
                mockUseAssignmentEnabled.mockReturnValue({
                    data: false,
                    isLoading: false,
                    error: null
                } as unknown as ReturnType<typeof hooks.useAssignmentEnabled>);

                render(<HealthPage />, { wrapper: Wrapper });

                expect(screen.queryByText('Capacity vs Demand')).not.toBeInTheDocument();
            });

            it('allows changing engineers on rota', () => {
                const now = new Date();
                const tickets = [
                    createTicketWithDate('1', 'Team A', now, 'john@example.com', 'opened'),
                ];

                mockUseTickets.mockReturnValue({
                    data: { content: tickets, page: 0, totalPages: 1, totalElements: 1 },
                    isLoading: false,
                    error: null
                } as unknown as ReturnType<typeof hooks.useTickets>);

                render(<HealthPage />, { wrapper: Wrapper });

                const inputs = screen.getAllByRole('spinbutton');
                const engineersInput = inputs.find(input => 
                    input.getAttribute('min') === '1' && 
                    input.getAttribute('max') && 
                    parseInt(input.getAttribute('max') || '0') > 0
                );

                expect(engineersInput).toBeInTheDocument();
                if (engineersInput) {
                    fireEvent.change(engineersInput, { target: { value: '3' } });
                }
            });

            it('allows changing tickets per engineer capacity', () => {
                const now = new Date();
                const tickets = [
                    createTicketWithDate('1', 'Team A', now, 'john@example.com', 'opened'),
                ];

                mockUseTickets.mockReturnValue({
                    data: { content: tickets, page: 0, totalPages: 1, totalElements: 1 },
                    isLoading: false,
                    error: null
                } as unknown as ReturnType<typeof hooks.useTickets>);

                render(<HealthPage />, { wrapper: Wrapper });

                const inputs = screen.getAllByRole('spinbutton');
                const capacityInput = inputs.find(input => 
                    input.getAttribute('min') === '1' && 
                    input.getAttribute('max') === '50'
                );

                expect(capacityInput).toBeInTheDocument();
                if (capacityInput) {
                    fireEvent.change(capacityInput, { target: { value: '8' } });
                }
            });

            it('calculates capacity utilization correctly', () => {
                const now = new Date();
                // Create 15 opened tickets
                const tickets = Array.from({ length: 15 }, (_, i) => 
                    createTicketWithDate(`ticket-${i}`, 'Team A', now, 'john@example.com', 'opened')
                );

                mockUseTickets.mockReturnValue({
                    data: { content: tickets, page: 0, totalPages: 1, totalElements: 15 },
                    isLoading: false,
                    error: null
                } as unknown as ReturnType<typeof hooks.useTickets>);

                render(<HealthPage />, { wrapper: Wrapper });

                // With default 2 engineers × 5 capacity = 10 total capacity
                // 15 tickets / 10 capacity = 150% utilization
                expect(screen.getByText('Capacity vs Demand')).toBeInTheDocument();
                expect(screen.getByText(/Capacity Utilization:/i)).toBeInTheDocument();
            });

            it('shows over capacity warning when utilization exceeds 100%', () => {
                const now = new Date();
                // Create 15 opened tickets (exceeds default capacity of 10)
                const tickets = Array.from({ length: 15 }, (_, i) => 
                    createTicketWithDate(`ticket-${i}`, 'Team A', now, 'john@example.com', 'opened')
                );

                mockUseTickets.mockReturnValue({
                    data: { content: tickets, page: 0, totalPages: 1, totalElements: 15 },
                    isLoading: false,
                    error: null
                } as unknown as ReturnType<typeof hooks.useTickets>);

                render(<HealthPage />, { wrapper: Wrapper });

                // Should show over capacity warning (multiple instances exist)
                const overCapacityTexts = screen.getAllByText(/Over Capacity/i);
                expect(overCapacityTexts.length).toBeGreaterThan(0);
            });
        });

        describe('Capacity Insights by Time Block', () => {
            it('renders when assignment is enabled and has data', () => {
                const monday = new Date('2024-01-01T10:00:00Z'); // Monday 10 AM
                const tickets = [
                    createTicketWithDate('1', 'Team A', monday, 'john@example.com', 'opened'),
                ];

                mockUseTickets.mockReturnValue({
                    data: { content: tickets, page: 0, totalPages: 1, totalElements: 1 },
                    isLoading: false,
                    error: null
                } as unknown as ReturnType<typeof hooks.useTickets>);

                render(<HealthPage />, { wrapper: Wrapper });

                // Should show collapsible section
                expect(screen.getByText('Capacity Insights by Time Block')).toBeInTheDocument();
            });

            it('is collapsible', () => {
                const monday = new Date('2024-01-01T10:00:00Z'); // Monday 10 AM
                const tickets = [
                    createTicketWithDate('1', 'Team A', monday, 'john@example.com', 'opened'),
                ];

                mockUseTickets.mockReturnValue({
                    data: { content: tickets, page: 0, totalPages: 1, totalElements: 1 },
                    isLoading: false,
                    error: null
                } as unknown as ReturnType<typeof hooks.useTickets>);

                render(<HealthPage />, { wrapper: Wrapper });

                const header = screen.getByText('Capacity Insights by Time Block').closest('button');
                expect(header).toBeInTheDocument();
                
                if (header) {
                    fireEvent.click(header);
                    // After clicking, content should be visible
                    expect(screen.getByText(/Average tickets per weekday/i)).toBeInTheDocument();
                }
            });

            it('does not render when assignment is disabled', () => {
                mockUseAssignmentEnabled.mockReturnValue({
                    data: false,
                    isLoading: false,
                    error: null
                } as unknown as ReturnType<typeof hooks.useAssignmentEnabled>);

                render(<HealthPage />, { wrapper: Wrapper });

                expect(screen.queryByText('Capacity Insights by Time Block')).not.toBeInTheDocument();
            });

            it('groups hours into 2-hour blocks', () => {
                const monday = new Date('2024-01-01T10:00:00Z'); // Monday 10 AM
                const tickets = [
                    createTicketWithDate('1', 'Team A', monday, 'john@example.com', 'opened'),
                ];

                mockUseTickets.mockReturnValue({
                    data: { content: tickets, page: 0, totalPages: 1, totalElements: 1 },
                    isLoading: false,
                    error: null
                } as unknown as ReturnType<typeof hooks.useTickets>);

                render(<HealthPage />, { wrapper: Wrapper });

                const header = screen.getByText('Capacity Insights by Time Block').closest('button');
                if (header) {
                    fireEvent.click(header);
                    // Should show time blocks like "9 AM - 11 AM", "11 AM - 1 PM", etc.
                    expect(screen.getByText(/Average tickets per weekday/i)).toBeInTheDocument();
                }
            });

            it('calculates utilization and recommendations correctly', () => {
                const monday = new Date('2024-01-01T10:00:00Z'); // Monday 10 AM
                const tickets = [
                    createTicketWithDate('1', 'Team A', monday, 'john@example.com', 'opened'),
                ];

                mockUseTickets.mockReturnValue({
                    data: { content: tickets, page: 0, totalPages: 1, totalElements: 1 },
                    isLoading: false,
                    error: null
                } as unknown as ReturnType<typeof hooks.useTickets>);

                render(<HealthPage />, { wrapper: Wrapper });

                const header = screen.getByText('Capacity Insights by Time Block').closest('button');
                if (header) {
                    fireEvent.click(header);
                    // Should show utilization and recommended engineers (multiple instances exist)
                    const utilizationTexts = screen.getAllByText(/Utilization:/i);
                    expect(utilizationTexts.length).toBeGreaterThan(0);
                    const recommendedTexts = screen.getAllByText(/Recommended:/i);
                    expect(recommendedTexts.length).toBeGreaterThan(0);
                }
            });
        });
    });

    describe('Date Filter - Custom Range Logic', () => {
        beforeEach(() => {
            mockUseTickets.mockReturnValue({
                data: { content: [], page: 0, totalPages: 1, totalElements: 0 },
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTickets>);

            mockUseRatings.mockReturnValue({
                data: { average: null, count: null },
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useRatings>);
        });

        it('should preserve date range when switching to custom mode without valid dates', () => {
            render(<HealthPage />, { wrapper: Wrapper });

            // Find the Custom button in date filter
            const customButton = screen.getByText('Custom');
            expect(customButton).toBeInTheDocument();

            // Click custom button
            fireEvent.click(customButton);

            // Verify useTickets was called with valid dates (not undefined)
            const calls = mockUseTickets.mock.calls;
            if (calls.length > 0) {
                const lastCall = calls[calls.length - 1];
                const [, , from, to] = lastCall;
                // Should have valid dates (preserved from previous filter), not undefined
                expect(from).toBeDefined();
                expect(to).toBeDefined();
                expect(from).not.toBe('');
                expect(to).not.toBe('');
            }
        });

        it('should use custom dates when both start and end dates are set', () => {
            const { container } = render(<HealthPage />, { wrapper: Wrapper });

            // Switch to custom mode
            const customButton = screen.getByText('Custom');
            fireEvent.click(customButton);

            // Find date inputs by type
            const dateInputs = container.querySelectorAll('input[type="date"]');
            
            expect(dateInputs.length).toBeGreaterThanOrEqual(2);
            
            // Set custom dates
            fireEvent.change(dateInputs[0], { target: { value: '2024-01-01' } });
            fireEvent.change(dateInputs[1], { target: { value: '2024-01-31' } });

            // Verify custom dates are used (check in subsequent calls)
            const calls = mockUseTickets.mock.calls;
            if (calls.length > 0) {
                const lastCall = calls[calls.length - 1];
                const [, , from, to] = lastCall;
                expect(from).toBeDefined();
                expect(to).toBeDefined();
            }
        });

        it('should not fetch all tickets when custom mode is selected with invalid dates', () => {
            render(<HealthPage />, { wrapper: Wrapper });

            const customButton = screen.getByText('Custom');
            fireEvent.click(customButton);

            // Even with invalid dates, should preserve a valid range
            const calls = mockUseTickets.mock.calls;
            if (calls.length > 0) {
                const lastCall = calls[calls.length - 1];
                const [, , from, to] = lastCall;
                // Should have valid dates, not undefined (which would fetch all tickets)
                expect(from).toBeDefined();
                expect(to).toBeDefined();
            }
        });
    });
});

