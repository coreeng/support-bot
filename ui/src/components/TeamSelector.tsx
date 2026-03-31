'use client'

import { useEffect, useMemo, useState } from 'react'
import { useAuth } from '@/hooks/useAuth'
import { useTeamFilter } from '@/contexts/TeamFilterContext'
import { useUrlParams } from '@/lib/hooks/useUrlParams'
import { Users } from 'lucide-react'
import {
    Dialog,
    DialogContent,
    DialogHeader,
    DialogTitle,
    DialogDescription,
    DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'

export default function TeamSelector() {
    const { user, isLeadership, isSupportEngineer } = useAuth()
    const { selectedTeam, setSelectedTeam } = useTeamFilter()
    const [teamParams, setTeamParam] = useUrlParams({ team: '' })

    // Set when the URL carries a ?team= value this user doesn't have access to.
    // Cleared when the user dismisses the modal.
    const [deniedTeam, setDeniedTeam] = useState<string | null>(null)

    const teams = useMemo(() => user?.teams ?? [], [user])
    const isRoleTeam = (t: { types?: string[] }) => (t.types || []).some(type => /leadership/i.test(type) || /support/i.test(type))
    const isEscalationTeam = (t: { types?: string[] }) => (t.types || []).some(type => /escalation/i.test(type))

    const tagForTypes = (types: string[] = []) => {
        if (types.some(type => /leadership/i.test(type))) return 'Leadership'
        if (types.some(type => /support/i.test(type))) return 'Support'
        if (types.some(type => /escalation/i.test(type))) return 'Escalation'
        if (types.some(type => /tenant/i.test(type))) return 'Tenant'
        return null
    }

    const tenantTeamNames = useMemo(
        () =>
            Array.from(new Set(
                teams
                .filter(t => !isRoleTeam(t) && !isEscalationTeam(t))
                .map(t => t.name)
                .filter(Boolean)
            )).sort(),
        [teams]
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

    const firstAvailableSelection =
        tenantTeamNames[0] || userEscalationTeams[0]?.name || userRoleTeams[0]?.name || null

    // All valid selection values
    const validSelections = useMemo(() => {
        const set = new Set(tenantTeamNames)
        userEscalationTeams.forEach(t => set.add(t.name))
        userRoleTeams.forEach(t => set.add(t.name))
        return set
    }, [tenantTeamNames, userEscalationTeams, userRoleTeams])

    // Ensure selectedTeam is initialised and valid, otherwise reset.
    // The URL ?team param takes priority over the auth-based default so that
    // bookmarked or shared URLs restore the intended view.
    useEffect(() => {
        const urlTeam = teamParams.team

        // URL team is present and valid — sync it into context.
        if (urlTeam && validSelections.size > 0 && validSelections.has(urlTeam)) {
            if (urlTeam !== selectedTeam) {
                setSelectedTeam(urlTeam)
            }
            return
        }

        // URL team is present but invalid for this user — show the access modal,
        // then clean up the URL.  The modal fires exactly once: after the URL is
        // corrected below, urlTeam will be valid on the next effect run and this
        // branch will no longer be entered.
        if (urlTeam && validSelections.size > 0 && !validSelections.has(urlTeam)) {
            // Local state update inside an effect is intentional: `deniedTeam` is not an effect
            // dependency so there is no cascade risk.  State (rather than a ref) is required here
            // so the modal stays open after the URL self-corrects below — a purely-derived value
            // would close the modal before the user can read it.
            // eslint-disable-next-line react-hooks/set-state-in-effect
            setDeniedTeam(urlTeam)
            if (selectedTeam && validSelections.has(selectedTeam)) {
                setTeamParam({ team: selectedTeam })
                return
            }
            // selectedTeam is also absent/invalid — fall through to the
            // initialisation/reset branches below, which will fix both.
        }

        // No valid URL team — apply the existing initialisation / reset logic
        // and write the resolved team back into the URL.
        if (!selectedTeam && firstAvailableSelection) {
            setSelectedTeam(firstAvailableSelection)
            return
        }
        if (selectedTeam && validSelections.size > 0 && !validSelections.has(selectedTeam)) {
            setSelectedTeam(firstAvailableSelection)
            setTeamParam({ team: firstAvailableSelection ?? '' })
        }
    }, [selectedTeam, validSelections, firstAvailableSelection, setSelectedTeam, teamParams.team, setTeamParam])

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
        : (firstAvailableSelection || '')

    // The team this user will fall back to — shown in the modal body.
    const fallbackTeam = (selectedTeam && validSelections.has(selectedTeam))
        ? selectedTeam
        : firstAvailableSelection

    return (
        <div className="space-y-2">
            {/* Access-denied modal — shown when the URL carries an inaccessible ?team= value */}
            <Dialog open={deniedTeam !== null} onOpenChange={(open) => { if (!open) setDeniedTeam(null) }}>
                <DialogContent data-testid="team-access-denied-modal">
                    <DialogHeader>
                        <DialogTitle>Team not available</DialogTitle>
                        <DialogDescription>
                            The link you followed requests the team{' '}
                            <strong>{deniedTeam}</strong>, which you don&apos;t have access to.
                            {fallbackTeam && (
                                <> <strong>{fallbackTeam}</strong> has been selected instead.</>
                            )}
                        </DialogDescription>
                    </DialogHeader>
                    <DialogFooter>
                        <Button onClick={() => setDeniedTeam(null)}>OK</Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>

            <div className="flex items-center gap-2 text-xs text-gray-400">
                <Users className="w-3 h-3" />
                <span>View as:</span>
            </div>
            <select
                data-testid="team-selector"
                value={displayValue}
                onChange={(e) => {
                    // Only update the URL here. The useEffect above will sync the context
                    // once the URL commits, avoiding a race where a stale searchParams
                    // snapshot in the page's own reset effect overwrites the new ?team= value.
                    setTeamParam({ team: e.target.value })
                }}
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

