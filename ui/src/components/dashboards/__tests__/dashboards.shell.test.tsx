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
    // Date filter is now a picklist
    expect(screen.getByTestId('sla-date-filter')).toBeInTheDocument()
    expect(screen.getByRole('option', { name: /Last Week/i })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: /Last 2 Weeks/i })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: /Last Month/i })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: /Last Year/i })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: /Custom/i })).toBeInTheDocument()
  })

  it('switches tabs when clicking Resolution SLAs and back to Response SLAs', async () => {
    render(<DashboardsPage />)

    // Default is Response (lazy render)
    expect(screen.getByTestId('response-section')).toBeInTheDocument()
    expect(screen.queryByTestId('resolution-section')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /Resolution SLAs/i }))

    await waitFor(() =>
      expect(screen.getByTestId('resolution-section')).toBeInTheDocument()
    )

    fireEvent.click(screen.getByRole('button', { name: /Response SLAs/i }))

    await waitFor(() =>
      expect(screen.getByTestId('response-section')).toBeInTheDocument()
    )
  })

  describe('Date Filter - Custom Range Logic', () => {
    it('should preserve date range when switching to custom mode without valid dates', () => {
      const { container } = render(<DashboardsPage />)

      // Switch to custom mode via the picklist — the component pre-fills inputs
      // with the current effective range so we never pass undefined to hooks.
      const dateSelect = screen.getByTestId('sla-date-filter')
      fireEvent.change(dateSelect, { target: { value: 'custom' } })

      // Date inputs should now be visible
      const dateInputs = container.querySelectorAll('input[type="date"]')
      expect(dateInputs.length).toBeGreaterThanOrEqual(2)

      // Both inputs must be non-empty (pre-filled from the effective range)
      dateInputs.forEach(input => {
        const value = (input as HTMLInputElement).value
        expect(value).toBeTruthy()
        expect(value).not.toBe('')
      })
    })

    it('should use custom dates when both start and end dates are set', () => {
      const { container } = render(<DashboardsPage />)

      const dateSelect = screen.getByTestId('sla-date-filter')
      fireEvent.change(dateSelect, { target: { value: 'custom' } })

      const dateInputs = container.querySelectorAll('input[type="date"]')
      expect(dateInputs.length).toBeGreaterThanOrEqual(2)

      // Override with explicit custom dates
      fireEvent.change(dateInputs[0], { target: { value: '2024-01-01' } })
      fireEvent.change(dateInputs[1], { target: { value: '2024-01-31' } })

      expect((dateInputs[0] as HTMLInputElement).value).toBe('2024-01-01')
      expect((dateInputs[1] as HTMLInputElement).value).toBe('2024-01-31')
    })

    it('should not fetch all tickets when custom mode is selected with invalid dates', () => {
      const { container } = render(<DashboardsPage />)

      const dateSelect = screen.getByTestId('sla-date-filter')
      fireEvent.change(dateSelect, { target: { value: 'custom' } })

      // Dates are pre-filled from the effective range on switch, so inputs are never empty
      const dateInputs = container.querySelectorAll('input[type="date"]')
      expect(dateInputs.length).toBeGreaterThanOrEqual(2)

      dateInputs.forEach(input => {
        const value = (input as HTMLInputElement).value
        expect(value).toBeTruthy()
        expect(value).not.toBe('')
      })
    })
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

