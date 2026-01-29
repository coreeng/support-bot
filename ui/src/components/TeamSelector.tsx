'use client'

import { useEffect } from 'react'
import { useUser } from '@/contexts/UserContext'
import { useTeamFilter } from '@/contexts/TeamFilterContext'
import { Users } from 'lucide-react'

export default function TeamSelector() {
    const { user, isLeadership, isSupportEngineer } = useUser()
    const { selectedTeam, setSelectedTeam } = useTeamFilter()

    // Business rule for showing dropdown:
    // - Show if user has leadership or support engineer role (even with only role teams)
    // - Show if user has any non-role team (tenant/escalation/etc.)
    const teams = user?.teams || []
    const isRoleTeam = (t: { types?: string[] }) => (t.types || []).some(type => /leadership/i.test(type) || /support/i.test(type))
    const isEscalationTeam = (t: { types?: string[] }) => (t.types || []).some(type => /escalation/i.test(type))
    const dataTeams = teams.filter(t => !isRoleTeam(t) && !isEscalationTeam(t)) // tenant/data teams

    const tagForTypes = (types: string[] = []) => {
        if (types.some(type => /leadership/i.test(type))) return 'Leadership'
        if (types.some(type => /support/i.test(type))) return 'Support'
        if (types.some(type => /escalation/i.test(type))) return 'Escalation'
        if (types.some(type => /tenant/i.test(type))) return 'Tenant'
        return null
    }

    type TeamOption = { name: string; badge: string | null; kind: 'data' | 'escalation' | 'role' }
    const priority: Record<TeamOption['kind'], number> = { data: 1, escalation: 2, role: 3 }

    const displayTeams = teams
        .filter(t => t.name)
        .reduce((acc: TeamOption[], team) => {
            const name = team.name
            const kind: TeamOption['kind'] = isRoleTeam(team) ? 'role' : isEscalationTeam(team) ? 'escalation' : 'data'
            const badge = tagForTypes(team.types)
            const existingIdx = acc.findIndex(item => item.name === name)
            if (existingIdx === -1) {
                acc.push({ name, badge, kind })
            } else if (priority[kind] > priority[acc[existingIdx].kind]) {
                acc[existingIdx] = { name, badge, kind }
            }
            return acc
        }, [])
    
    const allTeams = displayTeams.map(t => t.name)
    
    // Ensure selectedTeam is valid (in filtered teams), otherwise reset to first team
    // Hooks must run unconditionally
    useEffect(() => {
        if (selectedTeam && allTeams.length > 0 && !allTeams.includes(selectedTeam)) {
            setSelectedTeam(allTeams[0])
        }
    }, [selectedTeam, allTeams, setSelectedTeam])

    // Show dropdown if user has teams AND is leadership/support engineer (even with only role teams)
    // OR if user has at least one data team
    const showDropdown = !!user && allTeams.length > 0 &&
        (dataTeams.length > 0 || isLeadership || isSupportEngineer)
    
    // If user has no teams at all, show a warning message
    if (user && teams.length === 0) {
        return (
            <div className="bg-yellow-50 border border-yellow-300 rounded-lg px-4 py-3 space-y-2">
                <div className="flex items-center gap-2 text-yellow-800">
                    <Users className="w-4 h-4" />
                    <span className="font-semibold text-sm">No Teams Assigned</span>
                </div>
                <p className="text-xs text-yellow-700">
                    You are not a member of any teams. Please contact your administrator to be added to a team to access tickets and dashboards.
                </p>
            </div>
        )
    }
    
    if (!showDropdown) {
        return null
    }
    
    const validSelectedTeam = selectedTeam && allTeams.includes(selectedTeam) 
        ? selectedTeam 
        : allTeams[0]
    
    const dataTeamOptions = displayTeams.filter(t => t.kind === 'data')
    const escalationTeamOptions = displayTeams.filter(t => t.kind === 'escalation')
    const roleTeamOptions = displayTeams.filter(t => t.kind === 'role')
    
    return (
        <div className="space-y-2">
            <div className="flex items-center gap-2 text-xs text-gray-400">
                <Users className="w-3 h-3" />
                <span>View as:</span>
            </div>
            <select
                data-testid="team-selector"
                value={validSelectedTeam}
                onChange={(e) => setSelectedTeam(e.target.value)}
                className="w-full text-xs border border-gray-600 rounded px-2 py-1.5 bg-gray-700 text-white focus:outline-none focus:ring-2 focus:ring-blue-500 hover:bg-gray-600 transition-colors max-h-56"
            >
                {dataTeamOptions.length > 0 && (
                    <>
                        <option disabled>— Teams —</option>
                        {dataTeamOptions.map(team => (
                            <option key={team.name} value={team.name}>
                                {team.name}{team.badge ? ` · ${team.badge}` : ''}
                            </option>
                        ))}
                    </>
                )}
                {escalationTeamOptions.length > 0 && (
                    <>
                        <option disabled>— Escalation Teams —</option>
                        {escalationTeamOptions.map(team => (
                            <option key={team.name} value={team.name}>
                                {team.name}{team.badge ? ` · ${team.badge}` : ''}
                            </option>
                        ))}
                    </>
                )}
                {roleTeamOptions.length > 0 && (
                    <>
                        <option disabled>— Access Roles —</option>
                        {roleTeamOptions.map(team => (
                            <option key={team.name} value={team.name}>
                                {team.name}{team.badge ? ` · ${team.badge}` : ''}
                            </option>
                        ))}
                    </>
                )}
            </select>
            {selectedTeam && !(isLeadership || isSupportEngineer) && (
                <p className="text-xs text-yellow-400 italic">
                    Viewing: {selectedTeam}
                </p>
            )}
        </div>
    )
}

