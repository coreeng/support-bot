import React from 'react'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { within } from '@testing-library/react'
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
jest.mock('../../../lib/hooks', () => ({
  useFirstResponsePercentiles: () => ({ data: { p50: 1, p90: 2 } }),
  useTicketResolutionPercentiles: () => ({ data: { p50: 1, p75: 2, p90: 3 } }),
  useFirstResponseDurationDistribution: () => ({ data: [] }),
  useUnattendedQueriesCount: () => ({ data: { count: 0 } }),
  useTicketResolutionDurationDistribution: () => ({ data: [] }),
  useResolutionTimesByWeek: () => ({ data: [] }),
  useUnresolvedTicketAges: () => ({ data: { p50: '1 day', p90: '2 days' } }),
  useIncomingVsResolvedRate: () => ({ data: [] }),
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
    expect(screen.getByRole('button', { name: /Last 7 Days/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Last Month/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Last Year/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Custom/i })).toBeInTheDocument()
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
      
      // Find the Custom button
      const customButton = screen.getByRole('button', { name: /Custom/i })
      expect(customButton).toBeInTheDocument()
      
      // Click custom button - this should preserve dates, not fetch all tickets
      fireEvent.click(customButton)
      
      // Find date inputs by type attribute
      const dateInputs = container.querySelectorAll('input[type="date"]')
      // Date inputs should be visible when custom mode is active
      expect(dateInputs.length).toBeGreaterThanOrEqual(2)
      
      // The dates should be initialized (not empty), preventing all tickets fetch
      dateInputs.forEach(input => {
        const value = (input as HTMLInputElement).value
        // Should have a date value (initialized from previous filter)
        expect(value).toBeTruthy()
        expect(value).not.toBe('')
      })
    })

    it('should use custom dates when both start and end dates are set', () => {
      const { container } = render(<DashboardsPage />)
      
      // Switch to custom mode
      const customButton = screen.getByRole('button', { name: /Custom/i })
      fireEvent.click(customButton)
      
      // Find date inputs by type
      const dateInputs = container.querySelectorAll('input[type="date"]')
      expect(dateInputs.length).toBeGreaterThanOrEqual(2)
      
      // Set custom dates
      fireEvent.change(dateInputs[0], { target: { value: '2024-01-01' } })
      fireEvent.change(dateInputs[1], { target: { value: '2024-01-31' } })
      
      // Verify dates are set
      expect((dateInputs[0] as HTMLInputElement).value).toBe('2024-01-01')
      expect((dateInputs[1] as HTMLInputElement).value).toBe('2024-01-31')
    })

    it('should not fetch all tickets when custom mode is selected with invalid dates', () => {
      const { container } = render(<DashboardsPage />)
      
      const customButton = screen.getByRole('button', { name: /Custom/i })
      fireEvent.click(customButton)
      
      // Even with invalid dates initially, the useEffect should ensure valid dates
      const dateInputs = container.querySelectorAll('input[type="date"]')
      
      expect(dateInputs.length).toBeGreaterThanOrEqual(2)
      
      // Dates should be initialized to prevent fetching all tickets
      dateInputs.forEach(input => {
        const value = (input as HTMLInputElement).value
        expect(value).toBeTruthy()
        expect(value).not.toBe('')
      })
    })
  })

  it('updates date range when quick filters are clicked', () => {
    render(<DashboardsPage />)

    fireEvent.click(screen.getByRole('button', { name: /Last 7 Days/i }))
    expect(screen.getByText(/Last 7 Days/i).className).toMatch(/bg-blue-600/)

    fireEvent.click(screen.getByRole('button', { name: /Last Month/i }))
    expect(screen.getByText(/Last Month/i).className).toMatch(/bg-blue-600/)
  })
})

