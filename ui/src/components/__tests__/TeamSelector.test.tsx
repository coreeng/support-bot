import React from 'react'
import { render, screen, fireEvent } from '@testing-library/react'
import TeamSelector from '../TeamSelector'

jest.mock('../../hooks/useAuth', () => ({
  useAuth: jest.fn(),
}))

jest.mock('../../contexts/TeamFilterContext', () => ({
  useTeamFilter: jest.fn(),
}))

const mockUseAuth = jest.requireMock('../../hooks/useAuth').useAuth as jest.Mock
const mockUseTeamFilter = jest.requireMock('../../contexts/TeamFilterContext').useTeamFilter as jest.Mock

const baseTeamFilter = () => ({
  selectedTeam: null,
  setSelectedTeam: jest.fn(),
})

const renderSelector = () => render(<TeamSelector />)

describe('TeamSelector', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    mockUseTeamFilter.mockReturnValue(baseTeamFilter())
  })

  it('does not render when there is no user', () => {
    mockUseAuth.mockReturnValue({ user: null })

    const { container } = renderSelector()
    expect(container).toBeEmptyDOMElement()
  })

  it('shows warning message when user has no teams', () => {
    mockUseAuth.mockReturnValue({
      user: { teams: [] },
      isLeadership: false,
      isSupportEngineer: false,
    })

    renderSelector()

    expect(screen.queryByRole('combobox')).toBeNull()
    expect(screen.getByText('No Teams Assigned')).toBeInTheDocument()
    expect(screen.getByText(/not a member of any teams/i)).toBeInTheDocument()
    expect(screen.getByText(/contact your administrator/i)).toBeInTheDocument()
  })

  it('shows dropdown when user has only role teams (leadership/support) and is leadership or support engineer', () => {
    mockUseAuth.mockReturnValue({
      user: {
        teams: [
          { name: 'Leadership Team', types: ['leadership'], groupRefs: [] },
          { name: 'Support Engineers', types: ['support'], groupRefs: [] },
        ],
      },
      isLeadership: true,
      isSupportEngineer: true,
    })

    renderSelector()

    // Dropdown should now be visible for leadership/support engineers even with only role teams
    expect(screen.getByRole('combobox')).toBeInTheDocument()
    expect(screen.getByRole('option', { name: /Leadership Team/i })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: /Support Engineers/i })).toBeInTheDocument()
  })

  it('shows dropdown when a non-role team exists and includes role teams as options', () => {
    mockUseAuth.mockReturnValue({
      user: {
        teams: [
          { name: 'Leadership Team', types: ['leadership'], groupRefs: [] },
          { name: 'Support Engineers', types: ['support'], groupRefs: [] },
          { name: 'Tenant A', types: ['tenant'], groupRefs: [] },
        ],
      },
      isLeadership: true,
      isSupportEngineer: true,
    })

    renderSelector()

    const select = screen.getByRole('combobox')
    const options = screen.getAllByRole('option').map((o) => o.textContent)

    expect(select).toBeInTheDocument()
    expect(options).toEqual(
      expect.arrayContaining([
        '— Teams —',
        'Tenant A · Tenant',
        '— Access Roles —',
        'Leadership Team · Leadership',
        'Support Engineers · Support',
      ])
    )
  })

  it('deduplicates team names when building options', () => {
    mockUseAuth.mockReturnValue({
      user: {
        teams: [
          { name: 'Tenant A', types: ['tenant'], groupRefs: [] },
          { name: 'Tenant A', types: ['tenant'], groupRefs: [] },
        ],
      },
      isLeadership: false,
      isSupportEngineer: false,
    })

    renderSelector()

    // One group header plus one deduped team option
    expect(screen.getAllByRole('option')).toHaveLength(2)
  })

  it('resets selected team to first option if current selection is no longer valid', async () => {
    const setSelectedTeam = jest.fn()
    mockUseTeamFilter.mockReturnValue({
      selectedTeam: 'Old Team',
      setSelectedTeam,
    })
    mockUseAuth.mockReturnValue({
      user: {
        teams: [
          { name: 'Tenant A', types: ['tenant'], groupRefs: [] },
          { name: 'Leadership Team', types: ['leadership'], groupRefs: [] },
        ],
      },
      isLeadership: true,
      isSupportEngineer: false,
    })

    renderSelector()

    expect(setSelectedTeam).toHaveBeenCalledWith('Tenant A')
  })

  it('selects a team when user changes the dropdown', async () => {
    const setSelectedTeam = jest.fn()
    mockUseTeamFilter.mockReturnValue({
      selectedTeam: 'Tenant A',
      setSelectedTeam,
    })
    mockUseAuth.mockReturnValue({
      user: {
        teams: [
          { name: 'Tenant A', types: ['tenant'], groupRefs: [] },
          { name: 'Leadership Team', types: ['leadership'], groupRefs: [] },
        ],
      },
      isLeadership: true,
      isSupportEngineer: false,
    })

    renderSelector()
    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'Leadership Team' } })

    expect(setSelectedTeam).toHaveBeenCalledWith('Leadership Team')
  })
})

