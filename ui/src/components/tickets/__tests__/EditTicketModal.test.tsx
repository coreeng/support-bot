import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import EditTicketModal from '../EditTicketModal';
import * as hooks from '../../../lib/hooks';
import { useAuth } from '../../../hooks/useAuth';

// Mock the hooks
jest.mock('../../../lib/hooks');
jest.mock('../../../hooks/useAuth');

const mockUseTicket = hooks.useTicket as jest.MockedFunction<typeof hooks.useTicket>;
const mockUseTenantTeams = hooks.useTenantTeams as jest.MockedFunction<typeof hooks.useTenantTeams>;
const mockUseTeamSuggestions = hooks.useTeamSuggestions as jest.MockedFunction<typeof hooks.useTeamSuggestions>;
const mockUseRegistry = hooks.useRegistry as jest.MockedFunction<typeof hooks.useRegistry>;
const mockUseSupportMembers = hooks.useSupportMembers as jest.MockedFunction<typeof hooks.useSupportMembers>;
const mockUseAssignmentEnabled = hooks.useAssignmentEnabled as jest.MockedFunction<typeof hooks.useAssignmentEnabled>;
const mockUseAuth = useAuth as jest.MockedFunction<typeof useAuth>;

// Mock fetch for API calls
const mockFetch = jest.fn();

const mockTicketDetails = {
    id: '123',
    status: 'opened',
    team: { name: 'Engineering' },
    impact: 'high',
    tags: ['bug', 'urgent'],
    escalations: [
        { id: 'esc1', team: { name: 'Support' } }
    ],
    logs: [
        { event: 'ticket opened', date: '2024-01-01T10:00:00Z' },
        { event: 'ticket closed', date: '2024-01-02T15:00:00Z' }
    ],
    query: { link: 'https://slack.com/thread/123' }
};

const mockTeams = [
    { name: 'Engineering' },
    { name: 'Support' },
    { name: 'Product' }
];

const mockTeamSuggestions = {
    suggestedTeams: ['Engineering'],
    otherTeams: ['Support', 'Product'],
};

const mockRegistry = {
    impacts: [
        { code: 'high', label: 'High Impact' },
        { code: 'medium', label: 'Medium Impact' },
        { code: 'low', label: 'Low Impact' }
    ],
    tags: [
        { code: 'bug', label: 'Bug' },
        { code: 'urgent', label: 'Urgent' },
        { code: 'feature', label: 'Feature Request' }
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

describe('EditTicketModal', () => {
    const mockOnOpenChange = jest.fn();
    const mockOnSuccess = jest.fn();

    const mockSupportEngineerAuth = () => mockUseAuth.mockReturnValue({
        isSupportEngineer: true,
        user: null,
        isLoading: false,
        isAuthenticated: true,
        isLeadership: false,
        isEscalationTeam: false,
        actualEscalationTeams: [],
        logout: jest.fn()
    });

    const mockReadOnlyAuth = () => mockUseAuth.mockReturnValue({
        isSupportEngineer: false,
        user: null,
        isLoading: false,
        isAuthenticated: true,
        isLeadership: false,
        isEscalationTeam: false,
        actualEscalationTeams: [],
        logout: jest.fn()
    });

    beforeEach(() => {
        jest.clearAllMocks();
        mockSupportEngineerAuth();

        mockUseTenantTeams.mockReturnValue({
            data: mockTeams,
            isLoading: false,
            error: null
        } as unknown as ReturnType<typeof hooks.useTenantTeams>);

        mockUseTeamSuggestions.mockReturnValue({
            data: mockTeamSuggestions,
            isLoading: false,
            isError: false,
            error: null
        } as unknown as ReturnType<typeof hooks.useTeamSuggestions>);

        mockUseRegistry.mockReturnValue({
            data: mockRegistry,
            isLoading: false,
            error: null
        } as unknown as ReturnType<typeof hooks.useRegistry>);

        mockUseTicket.mockReturnValue({
            data: mockTicketDetails,
            isLoading: false,
            error: null
        } as unknown as ReturnType<typeof hooks.useTicket>);

        mockUseSupportMembers.mockReturnValue({
            data: [],
            isLoading: false,
            error: null
        } as unknown as ReturnType<typeof hooks.useSupportMembers>);

        mockUseAssignmentEnabled.mockReturnValue({
            data: false,
            isLoading: false,
            error: null
        } as unknown as ReturnType<typeof hooks.useAssignmentEnabled>);

        global.fetch = mockFetch;
        mockFetch.mockResolvedValue({
            ok: true,
            json: () => Promise.resolve(mockTicketDetails),
        } as Response);
    });

    describe('Modal Visibility', () => {
        it('renders modal when open is true', () => {
            render(
                <EditTicketModal
                    ticketId="123"
                    open={true}
                    onOpenChange={mockOnOpenChange}
                    onSuccess={mockOnSuccess}
                />,
                { wrapper: Wrapper }
            );

            expect(screen.getByText(/Ticket #123/i)).toBeInTheDocument();
        });

        it('does not render modal when open is false', () => {
            render(
                <EditTicketModal
                    ticketId="123"
                    open={false}
                    onOpenChange={mockOnOpenChange}
                    onSuccess={mockOnSuccess}
                />,
                { wrapper: Wrapper }
            );

            expect(screen.queryByText(/Ticket #123/i)).not.toBeInTheDocument();
        });
    });

    describe('Support Engineer Authorization', () => {
        it('shows edit controls for support engineers', () => {
            render(
                <EditTicketModal
                    ticketId="123"
                    open={true}
                    onOpenChange={mockOnOpenChange}
                    onSuccess={mockOnSuccess}
                />,
                { wrapper: Wrapper }
            );

            expect(screen.getByText('Save Changes')).toBeInTheDocument();
            expect(screen.getByText('Cancel')).toBeInTheDocument();
            expect(screen.queryByText('(Read-only)')).not.toBeInTheDocument();
        });

        it('shows read-only view for non-support engineers', () => {
            mockReadOnlyAuth();

            render(
                <EditTicketModal
                    ticketId="123"
                    open={true}
                    onOpenChange={mockOnOpenChange}
                    onSuccess={mockOnSuccess}
                />,
                { wrapper: Wrapper }
            );

            expect(screen.getByText('(Read-only)')).toBeInTheDocument();
            // Use getByRole to find the Close button in the footer (not the X close button)
            const closeButtons = screen.getAllByText('Close');
            expect(closeButtons.length).toBeGreaterThan(0);
            expect(screen.queryByText('Save Changes')).not.toBeInTheDocument();
            expect(screen.queryByText('Cancel')).not.toBeInTheDocument();
        });

        it('displays read-only description for non-support engineers', () => {
            mockReadOnlyAuth();

            render(
                <EditTicketModal
                    ticketId="123"
                    open={true}
                    onOpenChange={mockOnOpenChange}
                    onSuccess={mockOnSuccess}
                />,
                { wrapper: Wrapper }
            );

            expect(screen.getByText(/Only support engineers can edit tickets/i)).toBeInTheDocument();
        });

        it('shows editable fields as dropdowns for support engineers', () => {
            render(
                <EditTicketModal
                    ticketId="123"
                    open={true}
                    onOpenChange={mockOnOpenChange}
                    onSuccess={mockOnSuccess}
                />,
                { wrapper: Wrapper }
            );

            // shadcn Select renders triggers with role=combobox; we expect at minimum:
            // status, impact, support engineer, author's team, and the tag MultiSelect-style trigger.
            const triggers = screen.getAllByRole('combobox');
            expect(triggers.length).toBeGreaterThanOrEqual(3);

            // The team trigger shows the current team text.
            expect(screen.getByText('Engineering')).toBeInTheDocument();
        });

        it('shows read-only fields for non-support engineers', () => {
            mockReadOnlyAuth();

            render(
                <EditTicketModal
                    ticketId="123"
                    open={true}
                    onOpenChange={mockOnOpenChange}
                    onSuccess={mockOnSuccess}
                />,
                { wrapper: Wrapper }
            );

            // Should not have select elements
            const selects = screen.queryAllByRole('combobox');
            expect(selects.length).toBe(0);
        });
    });

    describe('Form Fields', () => {

        it('displays all required sections', () => {
            render(
                <EditTicketModal
                    ticketId="123"
                    open={true}
                    onOpenChange={mockOnOpenChange}
                    onSuccess={mockOnSuccess}
                />,
                { wrapper: Wrapper }
            );

            expect(screen.getByText(/Status History/i)).toBeInTheDocument();
            expect(screen.getByText(/Escalations/i)).toBeInTheDocument();
            expect(screen.getByText(/Change Status/i)).toBeInTheDocument();
            expect(screen.getByText(/Author's Team/i)).toBeInTheDocument();
            expect(screen.getByText(/^Tags$/i)).toBeInTheDocument();
            expect(screen.getByLabelText(/Impact/i)).toBeInTheDocument();
        });

        it('initializes form fields with ticket data', () => {
            render(
                <EditTicketModal
                    ticketId="123"
                    open={true}
                    onOpenChange={mockOnOpenChange}
                    onSuccess={mockOnSuccess}
                />,
                { wrapper: Wrapper }
            );

            // The shadcn Select trigger (role=combobox) for Change Status renders the resolved value.
            const statusTrigger = screen.getByLabelText(/Change Status/i);
            expect(statusTrigger).toHaveTextContent(/Opened/i);

            const impactTrigger = screen.getByLabelText(/Impact/i);
            expect(impactTrigger).toHaveTextContent(/High/i);

            // Team trigger shows the current team value.
            expect(screen.getByText('Engineering')).toBeInTheDocument();
        });

        it('displays selected tags as chips', () => {
            render(
                <EditTicketModal
                    ticketId="123"
                    open={true}
                    onOpenChange={mockOnOpenChange}
                    onSuccess={mockOnSuccess}
                />,
                { wrapper: Wrapper }
            );

            expect(screen.getByText('Bug')).toBeInTheDocument();
            expect(screen.getByText('Urgent')).toBeInTheDocument();
        });

    });

    describe('Save Functionality', () => {

        it('calls onSuccess callback after successful save', async () => {
            render(
                <EditTicketModal
                    ticketId="123"
                    open={true}
                    onOpenChange={mockOnOpenChange}
                    onSuccess={mockOnSuccess}
                />,
                { wrapper: Wrapper }
            );

            const saveButton = screen.getByText('Save Changes');
            fireEvent.click(saveButton);

            await waitFor(() => {
                expect(mockOnSuccess).toHaveBeenCalled();
            });
        });

        it('closes modal after successful save', async () => {
            render(
                <EditTicketModal
                    ticketId="123"
                    open={true}
                    onOpenChange={mockOnOpenChange}
                    onSuccess={mockOnSuccess}
                />,
                { wrapper: Wrapper }
            );

            const saveButton = screen.getByText('Save Changes');
            fireEvent.click(saveButton);

            await waitFor(() => {
                expect(mockOnOpenChange).toHaveBeenCalledWith(false);
            });
        });

        it('displays error message when save fails', async () => {
            mockFetch.mockResolvedValue({
                ok: false,
                status: 500,
            } as Response);

            render(
                <EditTicketModal
                    ticketId="123"
                    open={true}
                    onOpenChange={mockOnOpenChange}
                    onSuccess={mockOnSuccess}
                />,
                { wrapper: Wrapper }
            );

            const saveButton = screen.getByText('Save Changes');
            fireEvent.click(saveButton);

            await waitFor(() => {
                expect(screen.getByText(/Failed to update ticket/i)).toBeInTheDocument();
            });

            // Modal should not close on error
            expect(mockOnOpenChange).not.toHaveBeenCalled();
        });

        it('disables save button while saving', async () => {
            let resolvePatch: (value: unknown) => void;
            const patchPromise = new Promise((resolve) => {
                resolvePatch = resolve;
            });
            mockFetch.mockReturnValue(patchPromise as Promise<Response>);

            render(
                <EditTicketModal
                    ticketId="123"
                    open={true}
                    onOpenChange={mockOnOpenChange}
                    onSuccess={mockOnSuccess}
                />,
                { wrapper: Wrapper }
            );

            const saveButton = screen.getByText('Save Changes');
            fireEvent.click(saveButton);

            // Button should show "Saving..." and be disabled
            expect(screen.getByText('Saving...')).toBeInTheDocument();
            expect(screen.getByText('Saving...').closest('button')).toBeDisabled();

            // Resolve the promise
            resolvePatch!(mockTicketDetails);
        });
    });

    describe('Loading States', () => {
        it('shows loading state when ticket data is loading', () => {
            mockUseTicket.mockReturnValue({
                data: null,
                isLoading: true,
                error: null
            } as unknown as ReturnType<typeof hooks.useTicket>);

            render(
                <EditTicketModal
                    ticketId="123"
                    open={true}
                    onOpenChange={mockOnOpenChange}
                    onSuccess={mockOnSuccess}
                />,
                { wrapper: Wrapper }
            );

            // Check for loading skeleton
            expect(screen.getByTestId('ticket-loading-skeleton')).toBeInTheDocument();
        });
    });

    describe('Slack Link', () => {
        it('displays Open in Slack button when link exists', () => {
            render(
                <EditTicketModal
                    ticketId="123"
                    open={true}
                    onOpenChange={mockOnOpenChange}
                    onSuccess={mockOnSuccess}
                />,
                { wrapper: Wrapper }
            );

            const slackLink = screen.getByText(/Open in Slack/i);
            expect(slackLink).toBeInTheDocument();
            expect(slackLink.closest('a')).toHaveAttribute('href', 'https://slack.com/thread/123');
        });

        it('does not display Open in Slack button when link does not exist', () => {
            mockUseTicket.mockReturnValue({
                data: { ...mockTicketDetails, query: null },
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTicket>);

            render(
                <EditTicketModal
                    ticketId="123"
                    open={true}
                    onOpenChange={mockOnOpenChange}
                    onSuccess={mockOnSuccess}
                />,
                { wrapper: Wrapper }
            );

            expect(screen.queryByText(/Open in Slack/i)).not.toBeInTheDocument();
        });
    });

    describe('Cancel Functionality', () => {
        it('closes modal when cancel is clicked', () => {
            render(
                <EditTicketModal
                    ticketId="123"
                    open={true}
                    onOpenChange={mockOnOpenChange}
                    onSuccess={mockOnSuccess}
                />,
                { wrapper: Wrapper }
            );

            // Get the Cancel button (not the X close button)
            const cancelButton = screen.getByRole('button', { name: /^Cancel$/i });
            fireEvent.click(cancelButton);

            expect(mockOnOpenChange).toHaveBeenCalledWith(false);
        });
    });

    describe('Form Validation', () => {

        it('disables save button when status is empty', () => {
            mockUseTicket.mockReturnValue({
                data: { ...mockTicketDetails, status: '' },
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTicket>);

            render(
                <EditTicketModal
                    ticketId="123"
                    open={true}
                    onOpenChange={mockOnOpenChange}
                    onSuccess={mockOnSuccess}
                />,
                { wrapper: Wrapper }
            );

            const saveButton = screen.getByText('Save Changes');
            expect(saveButton).toBeDisabled();
        });

        it('disables save button when impact is empty', () => {
            mockUseTicket.mockReturnValue({
                data: { ...mockTicketDetails, impact: '' },
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTicket>);

            render(
                <EditTicketModal
                    ticketId="123"
                    open={true}
                    onOpenChange={mockOnOpenChange}
                    onSuccess={mockOnSuccess}
                />,
                { wrapper: Wrapper }
            );

            const saveButton = screen.getByText('Save Changes');
            expect(saveButton).toBeDisabled();
        });

        it('disables save button when author team is empty', () => {
            mockUseTicket.mockReturnValue({
                data: { ...mockTicketDetails, team: null },
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTicket>);

            render(
                <EditTicketModal
                    ticketId="123"
                    open={true}
                    onOpenChange={mockOnOpenChange}
                    onSuccess={mockOnSuccess}
                />,
                { wrapper: Wrapper }
            );

            const saveButton = screen.getByText('Save Changes');
            expect(saveButton).toBeDisabled();
        });

        it('disables save button when tags are empty', () => {
            mockUseTicket.mockReturnValue({
                data: { ...mockTicketDetails, tags: [] },
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTicket>);

            render(
                <EditTicketModal
                    ticketId="123"
                    open={true}
                    onOpenChange={mockOnOpenChange}
                    onSuccess={mockOnSuccess}
                />,
                { wrapper: Wrapper }
            );

            const saveButton = screen.getByText('Save Changes');
            expect(saveButton).toBeDisabled();
        });

        it('enables save button when all fields are filled', () => {
            render(
                <EditTicketModal
                    ticketId="123"
                    open={true}
                    onOpenChange={mockOnOpenChange}
                    onSuccess={mockOnSuccess}
                />,
                { wrapper: Wrapper }
            );

            const saveButton = screen.getByText('Save Changes');
            expect(saveButton).not.toBeDisabled();
        });

    });

    describe('Ticket Message Display', () => {
        it('displays ticket message when query.text is present', () => {
            mockUseTicket.mockReturnValue({
                data: {
                    ...mockTicketDetails,
                    query: {
                        link: 'https://slack.com/thread/123',
                        text: 'This is the original ticket message with `code` formatting',
                        date: '2024-01-01T10:00:00Z'
                    }
                },
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTicket>);

            render(
                <EditTicketModal
                    ticketId="123"
                    open={true}
                    onOpenChange={mockOnOpenChange}
                    onSuccess={mockOnSuccess}
                />,
                { wrapper: Wrapper }
            );

            expect(screen.getByText('Ticket Message')).toBeInTheDocument();
            expect(screen.getByText(/This is the original ticket message/)).toBeInTheDocument();
            expect(screen.getByText('code')).toBeInTheDocument(); // Inline code
        });

        it('does not display ticket message section when query.text is not present', () => {
            mockUseTicket.mockReturnValue({
                data: {
                    ...mockTicketDetails,
                    query: {
                        link: 'https://slack.com/thread/123'
                    }
                },
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTicket>);

            render(
                <EditTicketModal
                    ticketId="123"
                    open={true}
                    onOpenChange={mockOnOpenChange}
                    onSuccess={mockOnSuccess}
                />,
                { wrapper: Wrapper }
            );

            expect(screen.queryByText('Ticket Message')).not.toBeInTheDocument();
        });
    });

    describe('Summary Display', () => {
        it('renders the ticket summary when present', () => {
            mockUseTicket.mockReturnValue({
                data: {
                    ...mockTicketDetails,
                    query: {
                        ...mockTicketDetails.query,
                        text: 'Original Slack message'
                    },
                    summary: 'Cache invalidation resolved the incident'
                },
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTicket>);

            render(
                <EditTicketModal
                    ticketId="123"
                    open={true}
                    onOpenChange={mockOnOpenChange}
                    onSuccess={mockOnSuccess}
                />,
                { wrapper: Wrapper }
            );

            expect(screen.getByText('AI Summary')).toBeInTheDocument();
            expect(screen.getByText('Cache invalidation resolved the incident')).toBeInTheDocument();
        });

        it('does not render the summary section when summary is missing', () => {
            mockUseTicket.mockReturnValue({
                data: { ...mockTicketDetails, summary: null },
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTicket>);

            render(
                <EditTicketModal
                    ticketId="123"
                    open={true}
                    onOpenChange={mockOnOpenChange}
                    onSuccess={mockOnSuccess}
                />,
                { wrapper: Wrapper }
            );

            expect(screen.queryByText('AI Summary')).not.toBeInTheDocument();
        });

        it('does not render the summary section when summary is whitespace-only', () => {
            mockUseTicket.mockReturnValue({
                data: { ...mockTicketDetails, summary: '   ' },
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTicket>);

            render(
                <EditTicketModal
                    ticketId="123"
                    open={true}
                    onOpenChange={mockOnOpenChange}
                    onSuccess={mockOnSuccess}
                />,
                { wrapper: Wrapper }
            );

            expect(screen.queryByText('AI Summary')).not.toBeInTheDocument();
        });
    });

    describe('Escalation Warning', () => {
        it('does not show warning when changing to closed with no escalations', () => {
            mockUseTicket.mockReturnValue({
                data: {
                    ...mockTicketDetails,
                    status: 'opened',
                    escalations: []
                },
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTicket>);

            render(
                <EditTicketModal
                    ticketId="123"
                    open={true}
                    onOpenChange={mockOnOpenChange}
                    onSuccess={mockOnSuccess}
                />,
                { wrapper: Wrapper }
            );

            // Change status to closed
            const statusSelect = screen.getByLabelText(/Change Status/i) as HTMLSelectElement;
            fireEvent.change(statusSelect, { target: { value: 'closed' } });

            // No warning should appear
            expect(screen.queryByText(/unresolved escalation/i)).not.toBeInTheDocument();
        });

        it('does not show warning when changing to closed with only resolved escalations', () => {
            mockUseTicket.mockReturnValue({
                data: {
                    ...mockTicketDetails,
                    status: 'opened',
                    escalations: [
                        { id: 'esc1', team: { name: 'Support' }, resolvedAt: '2024-01-01T10:00:00Z' }
                    ]
                },
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTicket>);

            render(
                <EditTicketModal
                    ticketId="123"
                    open={true}
                    onOpenChange={mockOnOpenChange}
                    onSuccess={mockOnSuccess}
                />,
                { wrapper: Wrapper }
            );

            // Change status to closed
            const statusSelect = screen.getByLabelText(/Change Status/i) as HTMLSelectElement;
            fireEvent.change(statusSelect, { target: { value: 'closed' } });

            // No warning should appear
            expect(screen.queryByText(/unresolved escalation/i)).not.toBeInTheDocument();
        });

        it('does not show warning when ticket is already closed', () => {
            mockUseTicket.mockReturnValue({
                data: {
                    ...mockTicketDetails,
                    status: 'closed',
                    escalations: [
                        { id: 'esc1', team: { name: 'Support' }, resolvedAt: null }
                    ]
                },
                isLoading: false,
                error: null
            } as unknown as ReturnType<typeof hooks.useTicket>);

            render(
                <EditTicketModal
                    ticketId="123"
                    open={true}
                    onOpenChange={mockOnOpenChange}
                    onSuccess={mockOnSuccess}
                />,
                { wrapper: Wrapper }
            );

            // No warning should appear (already closed)
            expect(screen.queryByText(/unresolved escalation/i)).not.toBeInTheDocument();
        });

    });
});
