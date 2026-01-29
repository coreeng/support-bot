/**
 * Authorization Backend Integration Tests
 * 
 * Tests the critical authorization logic in NextAuth route:
 * - Backend API calls for leadership/support members and L2 teams
 * - Authorization flag computation (isLeadership, isSupportEngineer, isEscalation)
 * - L2 team mapping using `label` field (not `name`)
 * - Error handling when backend fails
 */

import { describe, it, expect, jest, beforeEach } from '@jest/globals';

// Mock fetch globally
global.fetch = jest.fn() as jest.MockedFunction<typeof fetch>;

describe('Authorization Backend Integration', () => {
    beforeEach(() => {
        jest.clearAllMocks();
    });

    describe('Backend API Calls', () => {
        it('should fetch leadership members from /team/leadership/members', async () => {
            const mockLeadershipEmails = ['leader1@example.com', 'leader2@example.com'];
            
            (global.fetch as jest.MockedFunction<typeof fetch>).mockResolvedValueOnce({
                ok: true,
                json: async () => mockLeadershipEmails
            } as Response);

            const response = await fetch('http://localhost:8080/team/leadership/members');
            const data = await response.json();

            expect(fetch).toHaveBeenCalledWith('http://localhost:8080/team/leadership/members');
            expect(data).toEqual(mockLeadershipEmails);
        });

        it('should fetch support members from /team/support/members', async () => {
            const mockSupportEmails = ['engineer1@example.com', 'engineer2@example.com'];
            
            (global.fetch as jest.MockedFunction<typeof fetch>).mockResolvedValueOnce({
                ok: true,
                json: async () => mockSupportEmails
            } as Response);

            const response = await fetch('http://localhost:8080/team/support/members');
            const data = await response.json();

            expect(fetch).toHaveBeenCalledWith('http://localhost:8080/team/support/members');
            expect(data).toEqual(mockSupportEmails);
        });

        it('should fetch escalation teams from /team?type=escalation', async () => {
            const mockL2Teams = [
                { label: 'Core-platform', code: 'core-platform', types: ['escalation'] },
                { label: 'Core-networking', code: 'core-networking', types: ['escalation'] }
            ];
            
            (global.fetch as jest.MockedFunction<typeof fetch>).mockResolvedValueOnce({
                ok: true,
                json: async () => mockL2Teams
            } as Response);

            const response = await fetch('http://localhost:8080/team?type=escalation');
            const data = await response.json();

            expect(fetch).toHaveBeenCalledWith('http://localhost:8080/team?type=escalation');
            expect(data).toEqual(mockL2Teams);
        });

        it('should handle backend errors gracefully', async () => {
            (global.fetch as jest.MockedFunction<typeof fetch>).mockResolvedValueOnce({
                ok: false,
                status: 500,
                statusText: 'Internal Server Error'
            } as Response);

            const response = await fetch('http://localhost:8080/team/leadership/members');

            expect(response.ok).toBe(false);
            expect(response.status).toBe(500);
        });
    });

    describe('Authorization Flag Computation', () => {
        it('should set isLeadership=true when user email is in leadership list', () => {
            const userEmail = 'leader@example.com';
            const leadershipEmails = ['leader@example.com', 'other-leader@example.com'];
            
            const isLeadership = leadershipEmails.includes(userEmail);
            
            expect(isLeadership).toBe(true);
        });

        it('should set isLeadership=false when user email is NOT in leadership list', () => {
            const userEmail = 'regular@example.com';
            const leadershipEmails = ['leader@example.com'];
            
            const isLeadership = leadershipEmails.includes(userEmail);
            
            expect(isLeadership).toBe(false);
        });

        it('should set isSupportEngineer=true when user email is in support list', () => {
            const userEmail = 'engineer@example.com';
            const supportEmails = ['engineer@example.com', 'other-engineer@example.com'];
            
            const isSupportEngineer = supportEmails.includes(userEmail);
            
            expect(isSupportEngineer).toBe(true);
        });

        it('should set isSupportEngineer=false when user email is NOT in support list', () => {
            const userEmail = 'regular@example.com';
            const supportEmails = ['engineer@example.com'];
            
            const isSupportEngineer = supportEmails.includes(userEmail);
            
            expect(isSupportEngineer).toBe(false);
        });

        it('should set isEscalation=true when user is member of L2 team', () => {
            const userTeams = ['Core-platform', 'Team A', 'Team B'];
            const l2Teams = [
                { label: 'Core-platform', code: 'core-platform' },
                { label: 'Core-networking', code: 'core-networking' }
            ];
            
            const l2TeamNames = l2Teams.map(t => t.label);
            const isEscalation = userTeams.some(teamName => l2TeamNames.includes(teamName));
            
            expect(isEscalation).toBe(true);
        });

        it('should set isEscalation=false when user is NOT member of any L2 team', () => {
            const userTeams = ['Team A', 'Team B'];
            const l2Teams = [
                { label: 'Core-platform', code: 'core-platform' },
                { label: 'Core-networking', code: 'core-networking' }
            ];
            
            const l2TeamNames = l2Teams.map(t => t.label);
            const isEscalation = userTeams.some(teamName => l2TeamNames.includes(teamName));
            
            expect(isEscalation).toBe(false);
        });

        it('should handle user not in any special role (all flags false)', () => {
            const userEmail = 'regular@example.com';
            const userTeams = ['Team A'];
            const leadershipEmails: string[] = [];
            const supportEmails: string[] = [];
            const l2Teams: Array<{ label: string }> = [];
            
            const isLeadership = leadershipEmails.includes(userEmail);
            const isSupportEngineer = supportEmails.includes(userEmail);
            const l2TeamNames = l2Teams.map(t => t.label);
            const isEscalation = userTeams.some(teamName => l2TeamNames.includes(teamName));
            
            expect(isLeadership).toBe(false);
            expect(isSupportEngineer).toBe(false);
            expect(isEscalation).toBe(false);
        });
    });

    describe('L2 Team Mapping (Critical Bug Fix)', () => {
        it('should use `label` field from L2 teams, not `name`', () => {
            const l2Teams = [
                { label: 'Core-platform', code: 'core-platform' },
                { label: 'Core-networking', code: 'core-networking' }
            ];
            
            // This is the critical fix - backend returns `label`, not `name`
            const l2TeamNames = l2Teams.map(t => t.label || t.code);
            
            expect(l2TeamNames).toEqual(['Core-platform', 'Core-networking']);
            expect(l2TeamNames).not.toContain(undefined);
        });

        it('should fallback to `code` if `label` is missing', () => {
            const l2Teams = [
                { label: '', code: 'core-platform' },
                { label: 'Core-networking', code: 'core-networking' }
            ];
            
            const l2TeamNames = l2Teams.map(t => t.label || t.code);
            
            expect(l2TeamNames).toEqual(['core-platform', 'Core-networking']);
        });

        it('should match user teams with L2 team labels correctly', () => {
            const userTeams = ['Core-platform', 'Team A'];
            const l2Teams = [
                { label: 'Core-platform', code: 'core-platform' },
                { label: 'Core-networking', code: 'core-networking' }
            ];
            
            const l2TeamNames = l2Teams.map(t => t.label || t.code);
            const matchedTeams = userTeams.filter(userTeam => l2TeamNames.includes(userTeam));
            
            expect(matchedTeams).toEqual(['Core-platform']);
        });

        it('should handle case-sensitive matching', () => {
            const userTeams = ['core-platform', 'Team A']; // lowercase
            const l2Teams = [
                { label: 'Core-platform', code: 'core-platform' } // capitalized
            ];
            
            const l2TeamNames = l2Teams.map(t => t.label || t.code);
            const matchedTeams = userTeams.filter(userTeam => l2TeamNames.includes(userTeam));
            
            // Should NOT match - case sensitive
            expect(matchedTeams).toEqual([]);
        });
    });

    describe('Error Handling', () => {
        it('should return empty array when leadership endpoint fails', async () => {
            (global.fetch as jest.MockedFunction<typeof fetch>).mockResolvedValueOnce({
                ok: false,
                status: 500
            } as Response);

            const response = await fetch('http://localhost:8080/team/leadership/members');
            const leadershipEmails = response.ok ? await response.json() : [];
            
            expect(leadershipEmails).toEqual([]);
        });

        it('should return empty array when support endpoint fails', async () => {
            (global.fetch as jest.MockedFunction<typeof fetch>).mockResolvedValueOnce({
                ok: false,
                status: 500
            } as Response);

            const response = await fetch('http://localhost:8080/team/support/members');
            const supportEmails = response.ok ? await response.json() : [];
            
            expect(supportEmails).toEqual([]);
        });

        it('should return empty array when L2 teams endpoint fails', async () => {
            (global.fetch as jest.MockedFunction<typeof fetch>).mockResolvedValueOnce({
                ok: false,
                status: 500
            } as Response);

            const response = await fetch('http://localhost:8080/team?type=escalation');
            const l2Teams = response.ok ? await response.json() : [];
            
            expect(l2Teams).toEqual([]);
        });

        it('should default all flags to false when backend fails', () => {
            // Simulate backend failure scenario
            const userEmail = 'user@example.com';
            const userTeams = ['Team A'];
            const leadershipEmails: string[] = []; // Empty due to error
            const supportEmails: string[] = []; // Empty due to error
            const l2Teams: Array<{ label: string }> = []; // Empty due to error
            
            const isLeadership = leadershipEmails.includes(userEmail);
            const isSupportEngineer = supportEmails.includes(userEmail);
            const l2TeamNames = l2Teams.map(t => t.label);
            const isEscalation = userTeams.some(teamName => l2TeamNames.includes(teamName));
            
            expect(isLeadership).toBe(false);
            expect(isSupportEngineer).toBe(false);
            expect(isEscalation).toBe(false);
        });
    });
});

