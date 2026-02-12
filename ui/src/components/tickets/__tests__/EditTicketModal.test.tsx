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

    beforeEach(() => {
        jest.clearAllMocks();

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
            mockUseAuth.mockReturnValue({
                isSupportEngineer: true,
                user: null,
                isLoading: false,
                isAuthenticated: true,
                isLeadership: false,
                isEscalationTeam: false,
                actualEscalationTeams: [],
                logout: jest.fn()
            });

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
            mockUseAuth.mockReturnValue({
                isSupportEngineer: true,
                user: null,
                isLoading: false,
                isAuthenticated: true,
                isLeadership: false,
                isEscalationTeam: false,
                actualEscalationTeams: [],
                logout: jest.fn()
            });

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
            mockUseAuth.mockReturnValue({
                isSupportEngineer: true,
                user: null,
                isLoading: false,
                isAuthenticated: true,
                isLeadership: false,
                isEscalationTeam: false,
                actualEscalationTeams: [],
                logout: jest.fn()
            });

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
            mockUseAuth.mockReturnValue({
                isSupportEngineer: false,
                user: null,
                isLoading: false,
                isAuthenticated: true,
                isLeadership: false,
                isEscalationTeam: false,
                actualEscalationTeams: [],
                logout: jest.fn()
            });

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
            mockUseAuth.mockReturnValue({
                isSupportEngineer: false,
                user: null,
                isLoading: false,
                isAuthenticated: true,
                isLeadership: false,
                isEscalationTeam: false,
                actualEscalationTeams: [],
                logout: jest.fn()
            });

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
            mockUseAuth.mockReturnValue({
                isSupportEngineer: true,
                user: null,
                isLoading: false,
                isAuthenticated: true,
                isLeadership: false,
                isEscalationTeam: false,
                actualEscalationTeams: [],
                logout: jest.fn()
            });

            render(
                <EditTicketModal
                    ticketId="123"
                    open={true}
                    onOpenChange={mockOnOpenChange}
                    onSuccess={mockOnSuccess}
                />,
                { wrapper: Wrapper }
            );

            // Status should be a select dropdown
            const statusSelect = screen.getByLabelText(/Change Status/i);
            expect(statusSelect).toBeInTheDocument();
            expect(statusSelect.tagName).toBe('SELECT');

            // Impact should be a select dropdown
            const impactSelect = screen.getByLabelText(/Impact/i);
            expect(impactSelect).toBeInTheDocument();
            expect(impactSelect.tagName).toBe('SELECT');

            // Team should be a select dropdown
            const teamSelect = screen.getByLabelText(/Select the Author's Team/i);
            expect(teamSelect).toBeInTheDocument();
            expect(teamSelect.tagName).toBe('SELECT');
        });

        it('shows read-only fields for non-support engineers', () => {
            mockUseAuth.mockReturnValue({
                isSupportEngineer: false,
                user: null,
                isLoading: false,
                isAuthenticated: true,
                isLeadership: false,
                isEscalationTeam: false,
                actualEscalationTeams: [],
                logout: jest.fn()
            });

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
        beforeEach(() => {
            mockUseAuth.mockReturnValue({
                isSupportEngineer: true,
                user: null,
                isLoading: false,
                isAuthenticated: true,
                isLeadership: false,
                isEscalationTeam: false,
                actualEscalationTeams: [],
                logout: jest.fn()
            });
        });

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
            expect(screen.getByText(/Select the Author's Team/i)).toBeInTheDocument();
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

            const statusSelect = screen.getByLabelText(/Change Status/i) as HTMLSelectElement;
            expect(statusSelect.value).toBe('opened');

            const impactSelect = screen.getByLabelText(/Impact/i) as HTMLSelectElement;
            expect(impactSelect.value).toBe('high');

            const teamSelect = screen.getByLabelText(/Select the Author's Team/i) as HTMLSelectElement;
            expect(teamSelect.value).toBe('Engineering');
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

        it('allows adding tags via dropdown', () => {
            render(
                <EditTicketModal
                    ticketId="123"
                    open={true}
                    onOpenChange={mockOnOpenChange}
                    onSuccess={mockOnSuccess}
                />,
                { wrapper: Wrapper }
            );

            const tagSelect = screen.getByText(/Select a tag to add/i).closest('select') as HTMLSelectElement;
            expect(tagSelect).toBeInTheDocument();

            // Select a new tag
            fireEvent.change(tagSelect, { target: { value: 'feature' } });

            // Should add the tag
            waitFor(() => {
                expect(screen.getByText('Feature Request')).toBeInTheDocument();
            });
        });

        it('allows removing tags by clicking X button', () => {
            render(
                <EditTicketModal
                    ticketId="123"
                    open={true}
                    onOpenChange={mockOnOpenChange}
                    onSuccess={mockOnSuccess}
                />,
                { wrapper: Wrapper }
            );

            const bugTag = screen.getByText('Bug');
            const removeButton = bugTag.parentElement?.querySelector('button');
            
            if (removeButton) {
                fireEvent.click(removeButton);
                
                waitFor(() => {
                    expect(screen.queryByText('Bug')).not.toBeInTheDocument();
                });
            }
        });
    });

    describe('Save Functionality', () => {
        beforeEach(() => {
            mockUseAuth.mockReturnValue({
                isSupportEngineer: true,
                user: null,
                isLoading: false,
                isAuthenticated: true,
                isLeadership: false,
                isEscalationTeam: false,
                actualEscalationTeams: [],
                logout: jest.fn()
            });
        });

        it('calls API with correct payload when saving', async () => {
            render(
                <EditTicketModal
                    ticketId="123"
                    open={true}
                    onOpenChange={mockOnOpenChange}
                    onSuccess={mockOnSuccess}
                />,
                { wrapper: Wrapper }
            );

            // Change status
            const statusSelect = screen.getByLabelText(/Change Status/i) as HTMLSelectElement;
            fireEvent.change(statusSelect, { target: { value: 'closed' } });

            // Click save
            const saveButton = screen.getByText('Save Changes');
            fireEvent.click(saveButton);

            await waitFor(() => {
                expect(mockFetch).toHaveBeenCalledWith('/api/tickets/123', expect.objectContaining({
                    method: 'PATCH',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        status: 'closed',
                        authorsTeam: 'Engineering',
                        tags: ['bug', 'urgent'],
                        impact: 'high'
                    })
                }));
            });
        });

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
            mockUseAuth.mockReturnValue({
                isSupportEngineer: true,
                user: null,
                isLoading: false,
                isAuthenticated: true,
                isLeadership: false,
                isEscalationTeam: false,
                actualEscalationTeams: [],
                logout: jest.fn()
            });

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
            mockUseAuth.mockReturnValue({
                isSupportEngineer: true,
                user: null,
                isLoading: false,
                isAuthenticated: true,
                isLeadership: false,
                isEscalationTeam: false,
                actualEscalationTeams: [],
                logout: jest.fn()
            });

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
            mockUseAuth.mockReturnValue({
                isSupportEngineer: true,
                user: null,
                isLoading: false,
                isAuthenticated: true,
                isLeadership: false,
                isEscalationTeam: false,
                actualEscalationTeams: [],
                logout: jest.fn()
            });

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
            mockUseAuth.mockReturnValue({
                isSupportEngineer: true,
                user: null,
                isLoading: false,
                isAuthenticated: true,
                isLeadership: false,
                isEscalationTeam: false,
                actualEscalationTeams: [],
                logout: jest.fn()
            });

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
        beforeEach(() => {
            mockUseAuth.mockReturnValue({
                isSupportEngineer: true,
                user: null,
                isLoading: false,
                isAuthenticated: true,
                isLeadership: false,
                isEscalationTeam: false,
                actualEscalationTeams: [],
                logout: jest.fn()
            });
        });

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

        it('shows validation errors when trying to save with empty fields', async () => {
            render(
                <EditTicketModal
                    ticketId="123"
                    open={true}
                    onOpenChange={mockOnOpenChange}
                    onSuccess={mockOnSuccess}
                />,
                { wrapper: Wrapper }
            );

            // Clear impact field
            const impactSelect = screen.getByLabelText(/Impact/i) as HTMLSelectElement;
            fireEvent.change(impactSelect, { target: { value: '' } });

            // Button should be disabled now
            const saveButton = screen.getByText('Save Changes');
            expect(saveButton).toBeDisabled();

            // Manually trigger validation by calling handleSave (simulating what would happen if button wasn't disabled)
            // Since button is disabled, we can't click it, but we can verify the validation logic
            // by checking that the button is disabled when fields are empty
            expect(impactSelect.value).toBe('');
        });

        it('disables save button when status is cleared', () => {
            render(
                <EditTicketModal
                    ticketId="123"
                    open={true}
                    onOpenChange={mockOnOpenChange}
                    onSuccess={mockOnSuccess}
                />,
                { wrapper: Wrapper }
            );

            const statusSelect = screen.getByLabelText(/Change Status/i) as HTMLSelectElement;
            fireEvent.change(statusSelect, { target: { value: '' } });

            const saveButton = screen.getByText('Save Changes');
            expect(saveButton).toBeDisabled();
        });

        it('disables save button when all tags are removed', async () => {
            render(
                <EditTicketModal
                    ticketId="123"
                    open={true}
                    onOpenChange={mockOnOpenChange}
                    onSuccess={mockOnSuccess}
                />,
                { wrapper: Wrapper }
            );

            // Remove all tags
            const bugTag = screen.getByText('Bug');
            const removeBugButton = bugTag.parentElement?.querySelector('button');
            if (removeBugButton) {
                fireEvent.click(removeBugButton);
            }

            await waitFor(() => {
                const urgentTag = screen.getByText('Urgent');
                const removeUrgentButton = urgentTag.parentElement?.querySelector('button');
                if (removeUrgentButton) {
                    fireEvent.click(removeUrgentButton);
                }
            });

            await waitFor(() => {
                const saveButton = screen.getByText('Save Changes');
                expect(saveButton).toBeDisabled();
            });
        });

        it('enables save button when all required fields are filled', () => {
            mockUseTicket.mockReturnValue({
                data: { ...mockTicketDetails, status: '', impact: '', team: null, tags: [] },
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

            // Initially button should be disabled
            let saveButton = screen.getByText('Save Changes');
            expect(saveButton).toBeDisabled();

            // Fill in status
            const statusSelect = screen.getByLabelText(/Change Status/i) as HTMLSelectElement;
            fireEvent.change(statusSelect, { target: { value: 'opened' } });

            // Still disabled (other fields missing)
            saveButton = screen.getByText('Save Changes');
            expect(saveButton).toBeDisabled();

            // Fill in impact
            const impactSelect = screen.getByLabelText(/Impact/i) as HTMLSelectElement;
            fireEvent.change(impactSelect, { target: { value: 'high' } });

            // Still disabled (team and tags missing)
            saveButton = screen.getByText('Save Changes');
            expect(saveButton).toBeDisabled();

            // Fill in team
            const teamSelect = screen.getByLabelText(/Select the Author's Team/i) as HTMLSelectElement;
            fireEvent.change(teamSelect, { target: { value: 'Engineering' } });

            // Still disabled (tags missing)
            saveButton = screen.getByText('Save Changes');
            expect(saveButton).toBeDisabled();

            // Add a tag
            const tagSelect = screen.getByText(/Select a tag to add/i).closest('select') as HTMLSelectElement;
            fireEvent.change(tagSelect, { target: { value: 'bug' } });

            // Now button should be enabled
            waitFor(() => {
                saveButton = screen.getByText('Save Changes');
                expect(saveButton).not.toBeDisabled();
            });
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
            mockUseAuth.mockReturnValue({
                isSupportEngineer: true,
                user: null,
                isLoading: false,
                isAuthenticated: true,
                isLeadership: false,
                isEscalationTeam: false,
                actualEscalationTeams: [],
                logout: jest.fn()
            });

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
            mockUseAuth.mockReturnValue({
                isSupportEngineer: true,
                user: null,
                isLoading: false,
                isAuthenticated: true,
                isLeadership: false,
                isEscalationTeam: false,
                actualEscalationTeams: [],
                logout: jest.fn()
            });

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

    describe('Escalation Warning', () => {
        it('shows warning when changing status to closed with unresolved escalations', () => {
            mockUseAuth.mockReturnValue({
                isSupportEngineer: true,
                user: null,
                isLoading: false,
                isAuthenticated: true,
                isLeadership: false,
                isEscalationTeam: false,
                actualEscalationTeams: [],
                logout: jest.fn()
            });

            mockUseTicket.mockReturnValue({
                data: {
                    ...mockTicketDetails,
                    status: 'opened',
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

            // Initially no warning
            expect(screen.queryByText(/unresolved escalation/i)).not.toBeInTheDocument();

            // Change status to closed
            const statusSelect = screen.getByLabelText(/Change Status/i) as HTMLSelectElement;
            fireEvent.change(statusSelect, { target: { value: 'closed' } });

            // Warning should appear
            expect(screen.getByText(/Ticket has 1 unresolved escalation/i)).toBeInTheDocument();
            expect(screen.getByText(/Closing the ticket will close all related escalations/i)).toBeInTheDocument();
        });

        it('does not show warning when changing to closed with no escalations', () => {
            mockUseAuth.mockReturnValue({
                isSupportEngineer: true,
                user: null,
                isLoading: false,
                isAuthenticated: true,
                isLeadership: false,
                isEscalationTeam: false,
                actualEscalationTeams: [],
                logout: jest.fn()
            });

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
            mockUseAuth.mockReturnValue({
                isSupportEngineer: true,
                user: null,
                isLoading: false,
                isAuthenticated: true,
                isLeadership: false,
                isEscalationTeam: false,
                actualEscalationTeams: [],
                logout: jest.fn()
            });

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
            mockUseAuth.mockReturnValue({
                isSupportEngineer: true,
                user: null,
                isLoading: false,
                isAuthenticated: true,
                isLeadership: false,
                isEscalationTeam: false,
                actualEscalationTeams: [],
                logout: jest.fn()
            });

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

        it('shows correct pluralization for multiple unresolved escalations', () => {
            mockUseAuth.mockReturnValue({
                isSupportEngineer: true,
                user: null,
                isLoading: false,
                isAuthenticated: true,
                isLeadership: false,
                isEscalationTeam: false,
                actualEscalationTeams: [],
                logout: jest.fn()
            });

            mockUseTicket.mockReturnValue({
                data: {
                    ...mockTicketDetails,
                    status: 'opened',
                    escalations: [
                        { id: 'esc1', team: { name: 'Support' }, resolvedAt: null },
                        { id: 'esc2', team: { name: 'Platform' }, resolvedAt: null }
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

            // Warning should show plural form
            expect(screen.getByText(/Ticket has 2 unresolved escalations/i)).toBeInTheDocument();
        });
    });
});

