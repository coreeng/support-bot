/**
 * EscalatedToMyTeamTable Unit Tests
 * 
 * Tests the visibility logic, filtering, and pagination for the
 * "Escalated to My Team" table on the Escalations tab.
 */

import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import EscalatedToMyTeamTable from '../EscalatedToMyTeamTable';
import * as hooks from '../../../lib/hooks';
import * as AuthContext from '../../../contexts/AuthContext';
import * as TeamFilterContext from '../../../contexts/TeamFilterContext';

// Mock hooks
jest.mock('../../../lib/hooks');
jest.mock('../../../contexts/AuthContext');
jest.mock('../../../contexts/TeamFilterContext');

const mockUseEscalations = hooks.useEscalations as jest.MockedFunction<typeof hooks.useEscalations>;
const mockUseRegistry = hooks.useRegistry as jest.MockedFunction<typeof hooks.useRegistry>;
const mockUseAuth = AuthContext.useAuth as jest.MockedFunction<typeof AuthContext.useAuth>;
const mockUseTeamFilter = TeamFilterContext.useTeamFilter as jest.MockedFunction<typeof TeamFilterContext.useTeamFilter>;

// Test data
const mockEscalations = [
    {
        id: 'esc-1',
        team: { name: 'Core-platform' },
        resolvedAt: null,
        impact: 'high',
        tags: ['bug', 'urgent'],
        ticketId: 'ticket-1',
        threadLink: 'http://slack.com/thread-1',
        openedAt: '2024-01-01T10:00:00Z'
    },
    {
        id: 'esc-2',
        team: { name: 'Core-platform' },
        resolvedAt: '2024-01-02T12:00:00Z',
        impact: 'medium',
        tags: ['feature'],
        ticketId: 'ticket-2',
        threadLink: 'http://slack.com/thread-2',
        openedAt: '2024-01-01T11:00:00Z'
    },
    {
        id: 'esc-3',
        team: { name: 'Core-platform' },
        resolvedAt: null,
        impact: 'low',
        tags: ['bug', 'question'],
        ticketId: 'ticket-3',
        threadLink: 'http://slack.com/thread-3',
        openedAt: '2024-01-01T12:00:00Z'
    },
    {
        id: 'esc-4',
        team: { name: 'Other-team' },
        resolvedAt: null,
        impact: 'high',
        tags: ['bug'],
        ticketId: 'ticket-4',
        threadLink: 'http://slack.com/thread-4',
        openedAt: '2024-01-01T13:00:00Z'
    }
];

const mockRegistry = {
    impacts: [
        { code: 'high', label: 'High' },
        { code: 'medium', label: 'Medium' },
        { code: 'low', label: 'Low' }
    ],
    tags: [
        { code: 'bug', label: 'Bug' },
        { code: 'feature', label: 'Feature' },
        { code: 'question', label: 'Question' },
        { code: 'urgent', label: 'Urgent' }
    ]
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

describe('EscalatedToMyTeamTable', () => {
    beforeEach(() => {
        jest.clearAllMocks();

        // Default mock implementations
        mockUseRegistry.mockReturnValue({
            data: mockRegistry,
            isLoading: false,
            error: null
        } as unknown as ReturnType<typeof hooks.useRegistry>);

        mockUseAuth.mockReturnValue({
            isEscalationTeam: true,
            actualEscalationTeams: ['Core-platform'],
            user: null,
            isLoading: false,
            isAuthenticated: false,
            isLeadership: false,
            isSupportEngineer: false,
            isLoadingEscalationTeams: false
        });

        mockUseTeamFilter.mockReturnValue({
            selectedTeam: 'Core-platform',
            setSelectedTeam: jest.fn(),
            hasFullAccess: false,
            effectiveTeams: ['Core-platform'],
            allTeams: ['Core-platform'],
            initialized: true
        });

        mockUseEscalations.mockReturnValue({
            data: { content: mockEscalations, page: 0, totalPages: 1, totalElements: 4 },
            isLoading: false,
            error: null
        } as unknown as ReturnType<typeof hooks.useEscalations>);
    });

    describe('Visibility Logic', () => {
        it('should show when user selects their escalation team', () => {
            render(<EscalatedToMyTeamTable />, { wrapper: Wrapper });

            expect(screen.getByText(/Escalated to My Team/i)).toBeInTheDocument();
        });

        it('should NOT show when user selects non-escalation team', () => {
            mockUseTeamFilter.mockReturnValue({
                selectedTeam: 'Other-team', // Not an escalation team
                setSelectedTeam: jest.fn(),
                hasFullAccess: false,
                effectiveTeams: ['Other-team'],
                allTeams: ['Core-platform', 'Other-team'],
                initialized: true
            });

            const { container } = render(<EscalatedToMyTeamTable />, { wrapper: Wrapper });

            expect(container.firstChild).toBeNull();
        });

        it('should NOT show when actualEscalationTeams is empty', () => {
            mockUseAuth.mockReturnValue({
                isEscalationTeam: false,
                actualEscalationTeams: [],
                user: null,
                isLoading: false,
                isAuthenticated: false,
                isLeadership: false,
                isSupportEngineer: false,
                isLoadingEscalationTeams: false
            });

            const { container } = render(<EscalatedToMyTeamTable />, { wrapper: Wrapper });

            expect(container.firstChild).toBeNull();
        });
    });

    describe('Filtering Logic', () => {
        it('should render filter dropdowns', () => {
            const { container } = render(<EscalatedToMyTeamTable />, { wrapper: Wrapper });

            // Should have 2 select dropdowns (status and impact)
            const selects = container.querySelectorAll('select');
            expect(selects.length).toBe(2);
        });

        it('should filter by status', () => {
            const { container } = render(<EscalatedToMyTeamTable />, { wrapper: Wrapper });

            const selects = container.querySelectorAll('select');
            const statusFilter = selects[0]; // First select is status

            // Change to resolved
            fireEvent.change(statusFilter, { target: { value: 'resolved' } });
            
            // Should still render the table
            expect(screen.getByText(/Escalated to My Team/i)).toBeInTheDocument();
        });

        it('should filter by impact', () => {
            const { container } = render(<EscalatedToMyTeamTable />, { wrapper: Wrapper });

            const selects = container.querySelectorAll('select');
            const impactFilter = selects[1]; // Second select is impact

            // Change to high
            fireEvent.change(impactFilter, { target: { value: 'high' } });

            // Should still render the table
            expect(screen.getByText(/Escalated to My Team/i)).toBeInTheDocument();
        });

        it('should display total count', () => {
            render(<EscalatedToMyTeamTable />, { wrapper: Wrapper });

            // Should show "3 total" for Core-platform escalations
            expect(screen.getByText('3 total')).toBeInTheDocument();
        });
    });

    describe('Top Tags', () => {
        it('should display top tags section', () => {
            render(<EscalatedToMyTeamTable />, { wrapper: Wrapper });

            // Should show "Top 2 Tags" heading
            expect(screen.getByText(/Top 2 Tags/i)).toBeInTheDocument();
        });

        it('should update when filters change', () => {
            const { container } = render(<EscalatedToMyTeamTable />, { wrapper: Wrapper });

            const selects = container.querySelectorAll('select');
            const statusFilter = selects[0]; // First select is status
            fireEvent.change(statusFilter, { target: { value: 'resolved' } });

            // Should still show Top 2 Tags heading
            expect(screen.getByText(/Top 2 Tags/i)).toBeInTheDocument();
        });
    });

    describe('Loading and Error States', () => {
        it('should show loading state when escalations are loading', () => {
            mockUseEscalations.mockReturnValue({
                data: undefined,
                isLoading: true,
                error: null
            } as unknown as ReturnType<typeof hooks.useEscalations>);

            render(<EscalatedToMyTeamTable />, { wrapper: Wrapper });

            expect(screen.getByText(/Loading/i)).toBeInTheDocument();
        });

        it('should show error state when escalations fail to load', () => {
            mockUseEscalations.mockReturnValue({
                data: undefined,
                isLoading: false,
                error: new Error('Failed to load')
            } as unknown as ReturnType<typeof hooks.useEscalations>);

            render(<EscalatedToMyTeamTable />, { wrapper: Wrapper });

            expect(screen.getByText(/Error loading escalations/i)).toBeInTheDocument();
        });

        it('should handle empty escalations data gracefully', () => {
            mockUseEscalations.mockReturnValue({
                data: { content: [], page: 0, totalPages: 0, totalElements: 0 },
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useEscalations>);

            render(<EscalatedToMyTeamTable />, { wrapper: Wrapper });

            expect(screen.getByText(/Escalated to My Team/i)).toBeInTheDocument();
            expect(screen.getByText(/No escalations/i)).toBeInTheDocument();
        });
    });
});

