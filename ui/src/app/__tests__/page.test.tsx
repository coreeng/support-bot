import { render, screen, waitFor } from '@testing-library/react'
import { useRouter } from 'next/navigation'
import Dashboard from '../page'
import { useAuth } from '../../hooks/useAuth'
import { useTeamFilter } from '../../contexts/TeamFilterContext'
import { useKnowledgeGapsEnabled } from '../../lib/hooks'

let mockSearchParamsValue = ''
const mockReplace = jest.fn()

jest.mock('next/navigation', () => ({
  useRouter: jest.fn(),
  usePathname: () => '/',
  useSearchParams: () => new URLSearchParams(mockSearchParamsValue),
}))

jest.mock('../../hooks/useAuth', () => ({
  useAuth: jest.fn(),
}))

jest.mock('../../contexts/TeamFilterContext', () => ({
  useTeamFilter: jest.fn(),
}))

jest.mock('../../lib/hooks', () => ({
  useKnowledgeGapsEnabled: jest.fn(),
}))

// Mock all the page components
jest.mock('../../components/stats/stats', () => ({
  __esModule: true,
  default: () => <div>Stats Page</div>,
}))

jest.mock('../../components/tickets/tickets', () => ({
  __esModule: true,
  default: () => <div>Tickets Page</div>,
}))

jest.mock('../../components/escalations/escalations', () => ({
  __esModule: true,
  default: () => <div>Escalations Page</div>,
}))

jest.mock('../../components/health/health', () => ({
  __esModule: true,
  default: () => <div>Health Page</div>,
}))

jest.mock('../../components/dashboards/dashboards', () => ({
  __esModule: true,
  default: () => <div>Dashboards Page</div>,
}))

jest.mock('../../components/knowledgegaps/knowledge-gaps', () => ({
  __esModule: true,
  default: () => <div>Knowledge Gaps Page</div>,
}))

jest.mock('../../components/TeamSelector', () => ({
  __esModule: true,
  default: () => <div>Team Selector</div>,
}))

jest.mock('next/image', () => ({
  __esModule: true,
  default: (props: any) => {
    // eslint-disable-next-line @next/next/no-img-element, jsx-a11y/alt-text
    return <img {...props} />
  },
}))

const mockRouter = {
  push: jest.fn(),
  replace: mockReplace,
}

const mockUseAuth = useAuth as unknown as jest.Mock
const mockUseTeamFilter = useTeamFilter as unknown as jest.Mock
const mockUseKnowledgeGapsEnabled = useKnowledgeGapsEnabled as unknown as jest.Mock
const mockUseRouter = useRouter as jest.MockedFunction<typeof useRouter>

describe('Dashboard - Support Area Summary visibility', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    mockSearchParamsValue = ''
    mockUseRouter.mockReturnValue(mockRouter as any)
    mockUseTeamFilter.mockReturnValue({
      hasFullAccess: true,
      selectedTeam: null,
      setSelectedTeam: jest.fn(),
      effectiveTeams: [],
      allTeams: [],
      initialized: true,
    })
  })

  describe('when feature is enabled and hasFullAccess is true', () => {
    beforeEach(() => {
      mockUseKnowledgeGapsEnabled.mockReturnValue({
        data: true,
        isLoading: false,
        error: null,
      } as any)
      mockUseTeamFilter.mockReturnValue({
        hasFullAccess: true,
        selectedTeam: null,
        setSelectedTeam: jest.fn(),
        effectiveTeams: [],
        allTeams: [],
        initialized: true,
      })
    })

    it('shows Support Area Summary when leadership/support team is selected', async () => {
      mockUseAuth.mockReturnValue({
        user: {
          name: 'User',
          email: 'user@example.com',
          roles: ['supportEngineer'],
          teams: [],
        },
        isLoading: false,
        isAuthenticated: true,
        logout: jest.fn(),
        refreshUser: jest.fn(),
        isLeadership: false,
        isEscalationTeam: false,
        isSupportEngineer: true,
        actualEscalationTeams: [],
      })

      render(<Dashboard />)

      await waitFor(() => {
        expect(screen.getByText('Support Area Summary')).toBeInTheDocument()
      })
    })
  })

  describe('when feature is enabled but hasFullAccess is false', () => {
    beforeEach(() => {
      mockUseKnowledgeGapsEnabled.mockReturnValue({
        data: true,
        isLoading: false,
        error: null,
      } as any)
      mockUseTeamFilter.mockReturnValue({
        hasFullAccess: false,
        selectedTeam: 'tenant-team',
        setSelectedTeam: jest.fn(),
        effectiveTeams: ['tenant-team'],
        allTeams: ['tenant-team'],
        initialized: true,
      })
    })

    it('does NOT show Support Area Summary when tenant team is selected', async () => {
      mockUseAuth.mockReturnValue({
        user: {
          name: 'Support User',
          email: 'support@example.com',
          roles: ['supportEngineer'],
          teams: [],
        },
        isLoading: false,
        isAuthenticated: true,
        logout: jest.fn(),
        refreshUser: jest.fn(),
        isLeadership: false,
        isEscalationTeam: false,
        isSupportEngineer: true,
        actualEscalationTeams: [],
      })

      render(<Dashboard />)

      await waitFor(() => {
        expect(screen.queryByText('Support Area Summary')).not.toBeInTheDocument()
      })
    })

    it('does NOT show Support Area Summary when escalation team is selected', async () => {
      mockUseAuth.mockReturnValue({
        user: {
          name: 'Leadership User',
          email: 'leader@example.com',
          roles: ['leadership'],
          teams: [],
        },
        isLoading: false,
        isAuthenticated: true,
        logout: jest.fn(),
        refreshUser: jest.fn(),
        isLeadership: true,
        isEscalationTeam: false,
        isSupportEngineer: false,
        actualEscalationTeams: [],
      })

      render(<Dashboard />)

      await waitFor(() => {
        expect(screen.queryByText('Support Area Summary')).not.toBeInTheDocument()
      })

      // Verify basic tabs are still visible
      expect(screen.getByText('Home')).toBeInTheDocument()
      expect(screen.getByText('Tickets')).toBeInTheDocument()
    })
  })

  describe('when feature is disabled', () => {
    beforeEach(() => {
      mockUseKnowledgeGapsEnabled.mockReturnValue({
        data: false,
        isLoading: false,
        error: null,
      } as any)
      mockUseTeamFilter.mockReturnValue({
        hasFullAccess: true,
        selectedTeam: null,
        setSelectedTeam: jest.fn(),
        effectiveTeams: [],
        allTeams: [],
        initialized: true,
      })
    })

    it('does NOT show Support Area Summary even with full access when feature is disabled', async () => {
      mockUseAuth.mockReturnValue({
        user: {
          name: 'User',
          email: 'user@example.com',
          roles: ['supportEngineer'],
          teams: [],
        },
        isLoading: false,
        isAuthenticated: true,
        logout: jest.fn(),
        refreshUser: jest.fn(),
        isLeadership: false,
        isEscalationTeam: false,
        isSupportEngineer: true,
        actualEscalationTeams: [],
      })

      render(<Dashboard />)

      await waitFor(() => {
        expect(screen.queryByText('Support Area Summary')).not.toBeInTheDocument()
      })
    })
  })

  describe('when feature status is loading', () => {
    beforeEach(() => {
      mockUseKnowledgeGapsEnabled.mockReturnValue({
        data: undefined,
        isLoading: true,
        error: null,
      } as any)
      mockUseTeamFilter.mockReturnValue({
        hasFullAccess: true,
        selectedTeam: null,
        setSelectedTeam: jest.fn(),
        effectiveTeams: [],
        allTeams: [],
        initialized: true,
      })
    })

    it('does NOT show Support Area Summary while loading even with full access', async () => {
      mockUseAuth.mockReturnValue({
        user: {
          name: 'User',
          email: 'user@example.com',
          roles: ['supportEngineer'],
          teams: [],
        },
        isLoading: false,
        isAuthenticated: true,
        logout: jest.fn(),
        refreshUser: jest.fn(),
        isLeadership: false,
        isEscalationTeam: false,
        isSupportEngineer: true,
        actualEscalationTeams: [],
      })

      render(<Dashboard />)

      await waitFor(() => {
        expect(screen.queryByText('Support Area Summary')).not.toBeInTheDocument()
      })
    })
  })

  describe('restricted tabs visibility', () => {
    beforeEach(() => {
      mockUseKnowledgeGapsEnabled.mockReturnValue({
        data: true,
        isLoading: false,
        error: null,
      } as any)
    })

    it('hides all restricted tabs (Analytics, SLA, Support Area Summary) when hasFullAccess is false', async () => {
      mockUseTeamFilter.mockReturnValue({
        hasFullAccess: false,
        selectedTeam: 'tenant-team',
        setSelectedTeam: jest.fn(),
        effectiveTeams: ['tenant-team'],
        allTeams: ['tenant-team'],
        initialized: true,
      })

      mockUseAuth.mockReturnValue({
        user: {
          name: 'User',
          email: 'user@example.com',
          roles: ['user'],
          teams: [],
        },
        isLoading: false,
        isAuthenticated: true,
        logout: jest.fn(),
        refreshUser: jest.fn(),
        isLeadership: false,
        isEscalationTeam: false,
        isSupportEngineer: false,
        actualEscalationTeams: [],
      })

      render(<Dashboard />)

      await waitFor(() => {
        expect(screen.queryByText('Analytics & Operations')).not.toBeInTheDocument()
        expect(screen.queryByText('SLA Dashboard')).not.toBeInTheDocument()
        expect(screen.queryByText('Support Area Summary')).not.toBeInTheDocument()
      })
    })

    it('shows all restricted tabs (except Support Area Summary) when hasFullAccess is true and feature disabled', async () => {
      mockUseKnowledgeGapsEnabled.mockReturnValue({
        data: false,
        isLoading: false,
        error: null,
      } as any)

      mockUseTeamFilter.mockReturnValue({
        hasFullAccess: true,
        selectedTeam: null,
        setSelectedTeam: jest.fn(),
        effectiveTeams: [],
        allTeams: [],
        initialized: true,
      })

      mockUseAuth.mockReturnValue({
        user: {
          name: 'User',
          email: 'user@example.com',
          roles: ['supportEngineer'],
          teams: [],
        },
        isLoading: false,
        isAuthenticated: true,
        logout: jest.fn(),
        refreshUser: jest.fn(),
        isLeadership: false,
        isEscalationTeam: false,
        isSupportEngineer: true,
        actualEscalationTeams: [],
      })

      render(<Dashboard />)

      await waitFor(() => {
        expect(screen.getByText('Analytics & Operations')).toBeInTheDocument()
        expect(screen.getByText('SLA Dashboard')).toBeInTheDocument()
        expect(screen.queryByText('Support Area Summary')).not.toBeInTheDocument()
      })
    })

    it('shows all basic tabs for any authenticated user', async () => {
      mockUseTeamFilter.mockReturnValue({
        hasFullAccess: false,
        selectedTeam: 'tenant-team',
        setSelectedTeam: jest.fn(),
        effectiveTeams: ['tenant-team'],
        allTeams: ['tenant-team'],
        initialized: true,
      })

      mockUseAuth.mockReturnValue({
        user: {
          name: 'User',
          email: 'user@example.com',
          roles: ['user'],
          teams: [],
        },
        isLoading: false,
        isAuthenticated: true,
        logout: jest.fn(),
        refreshUser: jest.fn(),
        isLeadership: false,
        isEscalationTeam: false,
        isSupportEngineer: false,
        actualEscalationTeams: [],
      })

      render(<Dashboard />)

      await waitFor(() => {
        expect(screen.getByText('Home')).toBeInTheDocument()
        expect(screen.getByText('Tickets')).toBeInTheDocument()
        expect(screen.getByText('Escalations')).toBeInTheDocument()
      })
    })
  })

  describe('URL tab deep-linking', () => {
    beforeEach(() => {
      mockUseKnowledgeGapsEnabled.mockReturnValue({
        data: true,
        isLoading: false,
        error: null,
      } as any)
      mockUseAuth.mockReturnValue({
        user: {
          name: 'User',
          email: 'user@example.com',
          roles: ['supportEngineer'],
          teams: [],
        },
        isLoading: false,
        isAuthenticated: true,
        logout: jest.fn(),
        refreshUser: jest.fn(),
        isLeadership: false,
        isEscalationTeam: false,
        isSupportEngineer: true,
        actualEscalationTeams: [],
      })
      mockUseTeamFilter.mockReturnValue({
        hasFullAccess: true,
        selectedTeam: null,
        setSelectedTeam: jest.fn(),
        effectiveTeams: [],
        allTeams: [],
        initialized: true,
      })
    })

    it('opens tickets tab when tab=tickets is present', async () => {
      mockSearchParamsValue = 'tab=tickets&ticketDate=lastWeek'
      render(<Dashboard />)

      await waitFor(() => {
        expect(screen.getByText('Tickets Page')).toBeInTheDocument()
      })
    })

    it('uses home tab for invalid tab values', async () => {
      mockSearchParamsValue = 'tab=not-a-real-tab'
      render(<Dashboard />)

      await waitFor(() => {
        expect(screen.getByText('Stats Page')).toBeInTheDocument()
      })
    })

    it('writes tab query param when switching tabs and preserves existing params', async () => {
      mockSearchParamsValue = 'foo=bar'
      render(<Dashboard />)

      const ticketsButton = await screen.findByRole('button', { name: 'Tickets' })
      ticketsButton.click()

      expect(mockReplace).toHaveBeenCalled()
      const [url] = mockReplace.mock.calls[mockReplace.mock.calls.length - 1]
      expect(url).toContain('/?')
      expect(url).toContain('foo=bar')
      expect(url).toContain('tab=tickets')
    })

    it('drops tickets-only params when switching to a non-tickets tab', async () => {
      mockSearchParamsValue = 'tab=tickets&ticketDate=lastWeek&ticketStatus=closed&ticketTeam=connected-app&ticketImpact=productionBlocking&foo=bar'
      render(<Dashboard />)

      const escalationsButton = await screen.findByRole('button', { name: 'Escalations' })
      escalationsButton.click()

      expect(mockReplace).toHaveBeenCalled()
      const [url] = mockReplace.mock.calls[mockReplace.mock.calls.length - 1]
      expect(url).toContain('tab=escalations')
      expect(url).toContain('foo=bar')
      expect(url).not.toContain('ticketDate=')
      expect(url).not.toContain('ticketStatus=')
      expect(url).not.toContain('ticketTeam=')
      expect(url).not.toContain('ticketImpact=')
    })

    it('drops section param when switching away from SLA tab', async () => {
      mockSearchParamsValue = 'tab=sla&section=response&foo=bar'
      render(<Dashboard />)

      const escalationsButton = await screen.findByRole('button', { name: 'Escalations' })
      escalationsButton.click()

      expect(mockReplace).toHaveBeenCalled()
      const [url] = mockReplace.mock.calls[mockReplace.mock.calls.length - 1]
      expect(url).toContain('tab=escalations')
      expect(url).toContain('foo=bar')
      expect(url).not.toContain('section=')
    })

    it('drops escalations-only params when switching to a non-escalations tab', async () => {
      mockSearchParamsValue = 'tab=escalations&escDate=lastWeek&escStatus=resolved&escTeam=Tenant%20Alpha&foo=bar'
      render(<Dashboard />)

      const ticketsButton = await screen.findByRole('button', { name: 'Tickets' })
      ticketsButton.click()

      expect(mockReplace).toHaveBeenCalled()
      const [url] = mockReplace.mock.calls[mockReplace.mock.calls.length - 1]
      expect(url).toContain('tab=tickets')
      expect(url).toContain('foo=bar')
      expect(url).not.toContain('escDate=')
      expect(url).not.toContain('escStatus=')
      expect(url).not.toContain('escTeam=')
    })
  })
})
