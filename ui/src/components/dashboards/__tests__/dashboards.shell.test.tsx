import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import DashboardsPage from '../dashboards'

jest.mock('../../../hooks/useAuth', () => ({
  useAuth: () => ({
    user: {
      email: 'test@example.com',
      teams: [{ name: 'Tenant A', types: ['tenant'], groupRefs: [] }],
    },
    isLoading: false,
    isAuthenticated: true,
  }),
}))

jest.mock('../../../contexts/TeamFilterContext', () => ({
  useTeamFilter: () => ({
    hasFullAccess: true,
  }),
}))

// Mock hooks to avoid real data fetching / QueryClient
// Mock useUrlParams with a useState-based implementation so section and filter
// changes re-render the component correctly.
jest.mock('../../../lib/hooks/useUrlParams', () => ({
  ...jest.requireActual('../../../lib/hooks/useUrlParams'),
  useUrlParams: (defaults: Record<string, string>) => {
    const { useState } = require('react') as typeof import('react')
    const [params, setParamsState] = useState<Record<string, string>>(defaults)
    const setParams = (updates: Record<string, string>) => {
      setParamsState((prev: Record<string, string>) => ({ ...prev, ...updates }))
    }
    return [params, setParams]
  },
}))

jest.mock('../../../lib/hooks', () => ({
  useFirstResponsePercentiles: () => ({ data: { p50: 1, p90: 2 } }),
  useTicketResolutionPercentiles: () => ({ data: { p50: 1, p75: 2, p90: 3 } }),
  useFirstResponseDurationDistribution: () => ({ data: [] }),
  useUnattendedQueriesCount: () => ({ data: { count: 0 } }),
  useTicketResolutionDurationDistribution: () => ({ data: [] }),
  useResolutionTimesByWeek: () => ({ data: [] }),
  useUnresolvedTicketAges: () => ({ data: { p50: '1 day', p90: '2 days' } }),
  useIncomingVsResolvedRate: () => ({ data: { granularity: 'DAY', data: [] } }),
  useAvgEscalationDurationByTag: () => ({ data: [] }),
  useEscalationPercentageByTag: () => ({ data: [] }),
  useEscalationTrendsByDate: () => ({ data: [] }),
  useEscalationsByTeam: () => ({ data: [] }),
  useEscalationsByImpact: () => ({ data: [] }),
  useWeeklyTicketCounts: () => ({ data: [] }),
  useWeeklyComparison: () => ({ data: [] }),
  useTopEscalatedTagsThisWeek: () => ({ data: [] }),
  useResolutionTimeByTag: () => ({ data: [] }),
}))

jest.mock('../ResponseSLASection', () => ({
  ResponseSLASection: () => <div data-testid="response-section">Response Section</div>,
}))
jest.mock('../ResolutionSLASection', () => ({
  ResolutionSLASection: () => <div data-testid="resolution-section">Resolution Section</div>,
}))
jest.mock('../EscalationSLASection', () => ({
  EscalationSLASection: () => <div data-testid="escalation-section">Escalation Section</div>,
}))
jest.mock('../WeeklyTrendsSection', () => ({
  WeeklyTrendsSection: () => <div data-testid="weekly-section">Weekly Section</div>,
}))

describe('Dashboards shell', () => {
  it('renders header and quick filters', () => {
    render(<DashboardsPage />)

    expect(screen.getByText('SLA Dashboards')).toBeInTheDocument()
    // Date filter trigger is rendered as a Radix Select combobox.
    expect(screen.getByTestId('sla-date-filter')).toBeInTheDocument()
  })

  it('updates date range when quick filters are changed', () => {
    render(<DashboardsPage />)

    const dateSelect = screen.getByTestId('sla-date-filter') as HTMLSelectElement

    fireEvent.change(dateSelect, { target: { value: 'lastWeek' } })
    expect(dateSelect.value).toBe('lastWeek')

    fireEvent.change(dateSelect, { target: { value: 'lastMonth' } })
    expect(dateSelect.value).toBe('lastMonth')
  })
})

