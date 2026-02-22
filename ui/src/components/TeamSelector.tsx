'use client'

import { useEffect, useMemo } from 'react'
import { useAuth } from '@/hooks/useAuth'
import { useTeamFilter } from '@/contexts/TeamFilterContext'
import { useTenantTeams } from '@/lib/hooks'
import { Users } from 'lucide-react'

export default function TeamSelector() {
    const { user, isLeadership, isSupportEngineer } = useAuth()
    const { selectedTeam, setSelectedTeam } = useTeamFilter()
    const { data: apiTenantTeams } = useTenantTeams()

    const teams = user?.teams || []
    const isRoleTeam = (t: { types?: string[] }) => (t.types || []).some(type => /leadership/i.test(type) || /support/i.test(type))
    const isEscalationTeam = (t: { types?: string[] }) => (t.types || []).some(type => /escalation/i.test(type))

    const tagForTypes = (types: string[] = []) => {
        if (types.some(type => /leadership/i.test(type))) return 'Leadership'
        if (types.some(type => /support/i.test(type))) return 'Support'
        if (types.some(type => /escalation/i.test(type))) return 'Escalation'
        if (types.some(type => /tenant/i.test(type))) return 'Tenant'
        return null
    }

    // Tenant teams from API (same source support engineers use)
    const tenantTeamNamesFromApi = useMemo(
        () => (apiTenantTeams?.map(t => t.name).filter(Boolean) ?? []).sort(),
        [apiTenantTeams]
    )

    // Fallback tenant teams from session for resiliency (e.g. backend team endpoint returns empty)
    const tenantTeamNamesFromSession = useMemo(
        () =>
            teams
                .filter(t => !isRoleTeam(t) && !isEscalationTeam(t))
                .map(t => t.name)
                .filter(Boolean)
                .sort(),
        [teams]
    )

    const tenantTeamNames = useMemo(
        () => Array.from(new Set([...tenantTeamNamesFromApi, ...tenantTeamNamesFromSession])),
        [tenantTeamNamesFromApi, tenantTeamNamesFromSession]
    )

    // User's escalation teams (from session)
    const userEscalationTeams = useMemo(
        () => teams.filter(t => isEscalationTeam(t) && !isRoleTeam(t)),
        [teams]
    )

    // User's role teams (from session — leadership/support)
    const userRoleTeams = useMemo(
        () => teams.filter(t => isRoleTeam(t)),
        [teams]
    )

    const firstUserTeam = teams[0]?.name

    // All valid selection values
    const validSelections = useMemo(() => {
        const set = new Set(tenantTeamNames)
        userEscalationTeams.forEach(t => set.add(t.name))
        userRoleTeams.forEach(t => set.add(t.name))
        if (firstUserTeam) set.add(firstUserTeam)
        return set
    }, [tenantTeamNames, userEscalationTeams, userRoleTeams, firstUserTeam])

    // Ensure selectedTeam is initialized and valid, otherwise reset.
    // Default stays aligned with user's first team in session ordering.
    useEffect(() => {
        if (!selectedTeam && firstUserTeam) {
            setSelectedTeam(firstUserTeam)
            return
        }
        if (selectedTeam && validSelections.size > 0 && !validSelections.has(selectedTeam)) {
            setSelectedTeam(firstUserTeam || tenantTeamNames[0] || null)
        }
    }, [selectedTeam, validSelections, tenantTeamNames, firstUserTeam, setSelectedTeam])

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

    if (!user || teams.length === 0) {
        return null
    }

    const displayValue = selectedTeam && validSelections.has(selectedTeam)
        ? selectedTeam
        : (firstUserTeam || tenantTeamNames[0] || '')

    return (
        <div className="space-y-2">
            <div className="flex items-center gap-2 text-xs text-gray-400">
                <Users className="w-3 h-3" />
                <span>View as:</span>
            </div>
            <select
                data-testid="team-selector"
                value={displayValue}
                onChange={(e) => setSelectedTeam(e.target.value)}
                className="w-full text-xs border border-gray-600 rounded px-2 py-1.5 bg-gray-700 text-white focus:outline-none focus:ring-2 focus:ring-blue-500 hover:bg-gray-600 transition-colors max-h-56"
            >
                {tenantTeamNames.length > 0 && (
                    <>
                        <option disabled>— Teams —</option>
                        {tenantTeamNames.map(name => (
                            <option key={name} value={name}>{name}</option>
                        ))}
                    </>
                )}
                {userEscalationTeams.length > 0 && (
                    <>
                        <option disabled>— Escalation Teams —</option>
                        {userEscalationTeams.map(t => (
                            <option key={t.name} value={t.name}>
                                {t.name} · Escalation
                            </option>
                        ))}
                    </>
                )}
                {userRoleTeams.length > 0 && (
                    <>
                        <option disabled>— Access Roles —</option>
                        {userRoleTeams.map(t => (
                            <option key={t.name} value={t.name}>
                                {t.name} · {tagForTypes(t.types)}
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

