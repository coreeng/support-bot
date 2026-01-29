/**
 * Test user personas for functional tests
 * Each persona represents a different user role with specific permissions
 */

export interface UserPersona {
    email: string;
    name: string;
    teams: Array<{ name: string; groupRefs: string[] }>;
    isLeadership: boolean;
    isSupportEngineer: boolean;
    isEscalation: boolean;
    actualEscalationTeams?: string[];
    description: string;
}

/**
 * Leadership member - Full access to all dashboards and data
 */
export const LeadershipMember: UserPersona = {
    email: 'leader@example.com',
    name: 'Leadership User',
    teams: [
        { name: 'test-support-leadership', groupRefs: [] },
        { name: 'team-a', groupRefs: [] }
    ],
    isLeadership: true,
    isSupportEngineer: false,
    isEscalation: false,
    description: 'Leadership member with full access to all dashboards'
};

/**
 * Support Engineer - Full access to all dashboards and data
 */
export const SupportEngineer: UserPersona = {
    email: 'engineer@example.com',
    name: 'Support Engineer',
    teams: [
        { name: 'test-support-engineers', groupRefs: [] },
        { name: 'team-a', groupRefs: [] }
    ],
    isLeadership: false,
    isSupportEngineer: true,
    isEscalation: false,
    description: 'Support engineer with full access to all dashboards'
};

/**
 * Escalation Team Member - Restricted view + escalation dashboards for their team
 */
export const EscalationTeamMember: UserPersona = {
    email: 'escalation@example.com',
    name: 'Escalation Team Member',
    teams: [
        { name: 'core-platform', groupRefs: [] },
        { name: 'team-a', groupRefs: [] }
    ],
    isLeadership: false,
    isSupportEngineer: false,
    isEscalation: true,
    actualEscalationTeams: ['core-platform'],
    description: 'Escalation team member - sees escalation dashboards when selecting core-platform'
};

/**
 * Regular Tenant - Restricted view only
 */
export const RegularTenant: UserPersona = {
    email: 'tenant@example.com',
    name: 'Regular Tenant',
    teams: [
        { name: 'team-a', groupRefs: [] }
    ],
    isLeadership: false,
    isSupportEngineer: false,
    isEscalation: false,
    description: 'Regular tenant with restricted view - no SLA, no Health dashboards'
};

/**
 * Multi-Team Member - Member of multiple teams, needs dropdown
 */
export const MultiTeamMember: UserPersona = {
    email: 'multiteam@example.com',
    name: 'Multi-Team Member',
    teams: [
        { name: 'team-a', groupRefs: [] },
        { name: 'team-b', groupRefs: [] },
        { name: 'team-c', groupRefs: [] }
    ],
    isLeadership: false,
    isSupportEngineer: false,
    isEscalation: false,
    description: 'Member of multiple tenant teams - can switch between them'
};

/**
 * All test personas
 */
export const Personas = {
    LeadershipMember,
    SupportEngineer,
    EscalationTeamMember,
    RegularTenant,
    MultiTeamMember
} as const;

/**
 * Helper to get L2 teams config for a persona
 */
export function getL2TeamsForPersona(persona: UserPersona): Array<{ label: string; code: string; types: string[] }> {
    if (!persona.isEscalation || !persona.actualEscalationTeams) {
        return [];
    }
    
    return persona.actualEscalationTeams.map(teamName => ({
        label: teamName,
        code: teamName.toLowerCase().replace(/\s+/g, '-'),
        types: ['escalation']
    }));
}

/**
 * Helper to get leadership emails config for a persona
 */
export function getLeadershipEmailsForPersona(persona: UserPersona): string[] {
    return persona.isLeadership ? [persona.email] : [];
}

/**
 * Helper to get support emails config for a persona
 */
export function getSupportEmailsForPersona(persona: UserPersona): string[] {
    return persona.isSupportEngineer ? [persona.email] : [];
}

