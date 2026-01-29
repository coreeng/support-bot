/**
 * Authorization Utilities Tests
 * 
 * Tests server-side authorization helper functions
 */

// Mock next-auth/jwt before importing authorization module
jest.mock('next-auth/jwt', () => ({
  getToken: jest.fn(),
}))

import {
  requireLeadership,
  requireEscalation,
  requireSupportEngineer,
  isMemberOfTeam,
  getUserTeams,
  AuthToken,
} from '../authorization'

describe('Authorization Utilities', () => {
  const mockLeadershipUser: AuthToken = {
    email: 'leader@example.com',
    name: 'Leadership User',
    teams: [
      { name: 'Leadership Team', types: ['leadership'], groupRefs: [] },
      { name: 'Support Team A', types: ['support'], groupRefs: [] },
    ],
    isLeadership: true,
    isEscalation: false,
    isSupportEngineer: true,
  }

  const mockEscalationUser: AuthToken = {
    email: 'escalation@example.com',
    name: 'Escalation User',
    teams: [
      { name: 'Escalation Team 1', types: ['escalation'], groupRefs: [] },
    ],
    isLeadership: false,
    isEscalation: true,
    isSupportEngineer: false,
  }

  const mockSupportUser: AuthToken = {
    email: 'support@example.com',
    name: 'Support User',
    teams: [
      { name: 'Support Team B', types: ['support'], groupRefs: [] },
    ],
    isLeadership: false,
    isEscalation: false,
    isSupportEngineer: true,
  }

  describe('requireLeadership', () => {
    it('returns true for leadership user', () => {
      expect(requireLeadership(mockLeadershipUser)).toBe(true)
    })

    it('returns false for non-leadership user', () => {
      expect(requireLeadership(mockSupportUser)).toBe(false)
    })

    it('returns false for null user', () => {
      expect(requireLeadership(null)).toBe(false)
    })
  })

  describe('requireEscalation', () => {
    it('returns true for escalation user', () => {
      expect(requireEscalation(mockEscalationUser)).toBe(true)
    })

    it('returns false for non-escalation user', () => {
      expect(requireEscalation(mockSupportUser)).toBe(false)
    })

    it('returns false for null user', () => {
      expect(requireEscalation(null)).toBe(false)
    })
  })

  describe('requireSupportEngineer', () => {
    it('returns true for support engineer', () => {
      expect(requireSupportEngineer(mockSupportUser)).toBe(true)
    })

    it('returns true for leadership user (who is also support engineer)', () => {
      expect(requireSupportEngineer(mockLeadershipUser)).toBe(true)
    })

    it('returns false for non-support engineer', () => {
      expect(requireSupportEngineer(mockEscalationUser)).toBe(false)
    })

    it('returns false for null user', () => {
      expect(requireSupportEngineer(null)).toBe(false)
    })
  })

  describe('isMemberOfTeam', () => {
    it('returns true when user is member of specified team', () => {
      expect(isMemberOfTeam(mockLeadershipUser, 'Leadership Team')).toBe(true)
      expect(isMemberOfTeam(mockLeadershipUser, 'Support Team A')).toBe(true)
    })

    it('returns false when user is not member of specified team', () => {
      expect(isMemberOfTeam(mockSupportUser, 'Leadership Team')).toBe(false)
    })

    it('returns false for null user', () => {
      expect(isMemberOfTeam(null, 'Any Team')).toBe(false)
    })

    it('returns false for user with no teams', () => {
      const userWithNoTeams: AuthToken = {
        email: 'user@example.com',
        name: 'User',
        teams: [],
        isLeadership: false,
        isEscalation: false,
        isSupportEngineer: false,
      }
      expect(isMemberOfTeam(userWithNoTeams, 'Any Team')).toBe(false)
    })
  })

  describe('getUserTeams', () => {
    it('returns array of team names for user', () => {
      const teams = getUserTeams(mockLeadershipUser)
      expect(teams).toEqual(['Leadership Team', 'Support Team A'])
    })

    it('returns empty array for user with no teams', () => {
      const userWithNoTeams: AuthToken = {
        email: 'user@example.com',
        name: 'User',
        teams: [],
        isLeadership: false,
        isEscalation: false,
        isSupportEngineer: false,
      }
      expect(getUserTeams(userWithNoTeams)).toEqual([])
    })

    it('returns empty array for null user', () => {
      expect(getUserTeams(null)).toEqual([])
    })
  })
})

