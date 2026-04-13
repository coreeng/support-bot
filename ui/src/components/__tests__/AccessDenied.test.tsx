import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { AccessDenied, RequireDashboardAccess } from '../AccessDenied';
import * as AuthHook from '../../hooks/useAuth';

jest.mock('../../hooks/useAuth');
const mockUseAuth = AuthHook.useAuth as jest.MockedFunction<typeof AuthHook.useAuth>;

function mockAuth(overrides: Partial<ReturnType<typeof AuthHook.useAuth>> = {}) {
    mockUseAuth.mockReturnValue({
        user: {
            id: 'user-1',
            email: 'user@example.com',
            name: 'Test User',
            teams: [],
            roles: [],
        },
        isLeadership: false,
        isSupportEngineer: false,
        isEscalationTeam: false,
        actualEscalationTeams: [],
        isLoading: false,
        isAuthenticated: true,
        logout: jest.fn(),
        ...overrides,
    });
}

describe('AccessDenied', () => {
    it('shows access restricted message with user email', () => {
        mockAuth({ user: { id: '1', email: 'dev@example.com', name: 'Dev', teams: [], roles: [] } });
        render(<AccessDenied />);

        expect(screen.getByText(/Access Restricted/i)).toBeInTheDocument();
        expect(screen.getByText(/Support Engineer/)).toBeInTheDocument();
        expect(screen.getByText(/Leadership/)).toBeInTheDocument();
        expect(screen.getByText(/dev@example.com/)).toBeInTheDocument();
    });

    it('calls logout when sign-out button is clicked', () => {
        const logout = jest.fn();
        mockAuth({ logout });
        render(<AccessDenied />);

        fireEvent.click(screen.getByText(/Sign in with a different account/i));
        expect(logout).toHaveBeenCalled();
    });

    it('renders nothing while loading', () => {
        mockAuth({ isLoading: true });
        const { container } = render(<AccessDenied />);
        expect(container.firstChild).toBeNull();
    });
});

describe('RequireDashboardAccess', () => {
    it('renders children for support engineers', () => {
        mockAuth({ isSupportEngineer: true });
        render(
            <RequireDashboardAccess>
                <div data-testid="protected-content">Dashboard</div>
            </RequireDashboardAccess>
        );

        expect(screen.getByTestId('protected-content')).toBeInTheDocument();
        expect(screen.queryByText(/Access Restricted/i)).not.toBeInTheDocument();
    });

    it('renders children for leadership', () => {
        mockAuth({ isLeadership: true });
        render(
            <RequireDashboardAccess>
                <div data-testid="protected-content">Dashboard</div>
            </RequireDashboardAccess>
        );

        expect(screen.getByTestId('protected-content')).toBeInTheDocument();
    });

    it('shows access denied for users without required roles', () => {
        mockAuth({ isLeadership: false, isSupportEngineer: false });
        render(
            <RequireDashboardAccess>
                <div data-testid="protected-content">Dashboard</div>
            </RequireDashboardAccess>
        );

        expect(screen.queryByTestId('protected-content')).not.toBeInTheDocument();
        expect(screen.getByText(/Access Restricted/i)).toBeInTheDocument();
    });

    it('shows loading spinner while auth is loading', () => {
        mockAuth({ isLoading: true });
        render(
            <RequireDashboardAccess>
                <div data-testid="protected-content">Dashboard</div>
            </RequireDashboardAccess>
        );

        expect(screen.queryByTestId('protected-content')).not.toBeInTheDocument();
        expect(screen.getByText(/Loading/i)).toBeInTheDocument();
    });
});
