'use client'

import { useEffect, useMemo, useState } from 'react'
import { useAuth } from '@/hooks/useAuth'
import { useTeamFilter } from '@/contexts/TeamFilterContext'
import { useUrlParams } from '@/lib/hooks/useUrlParams'
import { AlertTriangle, Check, Users } from 'lucide-react'
import {
    Dialog,
    DialogContent,
    DialogHeader,
    DialogTitle,
    DialogDescription,
    DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuLabel,
    DropdownMenuSeparator,
    DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'

export default function TeamSelector() {
    const { user, isLeadership, isSupportEngineer } = useAuth()
    const { selectedTeam, setSelectedTeam } = useTeamFilter()
    const [teamParams, setTeamParam] = useUrlParams({ team: '' })

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

    const userEscalationTeams = useMemo(
        () => teams.filter(t => isEscalationTeam(t) && !isRoleTeam(t)),
        [teams]
    )

    const userRoleTeams = useMemo(
        () => teams.filter(t => isRoleTeam(t)),
        [teams]
    )

    const firstAvailableSelection =
        tenantTeamNames[0] || userEscalationTeams[0]?.name || userRoleTeams[0]?.name || null

    const validSelections = useMemo(() => {
        const set = new Set(tenantTeamNames)
        userEscalationTeams.forEach(t => set.add(t.name))
        userRoleTeams.forEach(t => set.add(t.name))
        return set
    }, [tenantTeamNames, userEscalationTeams, userRoleTeams])

    useEffect(() => {
        const urlTeam = teamParams.team

        if (urlTeam && validSelections.size > 0 && validSelections.has(urlTeam)) {
            if (urlTeam !== selectedTeam) {
                setSelectedTeam(urlTeam)
            }
            return
        }

        if (urlTeam && validSelections.size > 0 && !validSelections.has(urlTeam)) {
            // eslint-disable-next-line react-hooks/set-state-in-effect
            setDeniedTeam(urlTeam)
            if (selectedTeam && validSelections.has(selectedTeam)) {
                setTeamParam({ team: selectedTeam })
                return
            }
        }

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
            <div className="group relative">
                <button
                    type="button"
                    aria-label="No teams assigned"
                    className="flex h-9 w-9 items-center justify-center rounded-md border border-warning/30 bg-warning/10 text-warning transition-colors hover:bg-warning/20 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                    <AlertTriangle className="h-4 w-4" />
                </button>
                <div
                    role="tooltip"
                    className="invisible absolute right-0 top-full z-50 mt-2 w-72 rounded-md border border-warning/30 bg-warning/10 px-3 py-2 text-warning opacity-0 shadow-md transition-opacity group-focus-within:visible group-focus-within:opacity-100 group-hover:visible group-hover:opacity-100"
                >
                    <div className="flex items-center gap-2">
                        <Users className="h-4 w-4" />
                        <span className="text-sm font-semibold">No Teams Assigned</span>
                    </div>
                    <p className="mt-1 text-xs leading-tight">
                        You are not a member of any teams. Please contact your administrator to be added to a team to access tickets and dashboards.
                    </p>
                </div>
            </div>
        )
    }

    if (!user || teams.length === 0) {
        return null
    }

    const displayValue = selectedTeam && validSelections.has(selectedTeam)
        ? selectedTeam
        : (firstAvailableSelection || '')

    const fallbackTeam = (selectedTeam && validSelections.has(selectedTeam))
        ? selectedTeam
        : firstAvailableSelection

    const handleSelect = (name: string) => {
        setTeamParam({ team: name })
    }

    const renderItem = (name: string, suffix?: string | null) => (
        <DropdownMenuItem
            key={name}
            onSelect={() => handleSelect(name)}
            className="cursor-pointer"
        >
            <Users className="mr-2 h-4 w-4" />
            <span className="flex-1 break-words">
                {name}
                {suffix && <span className="ml-1 text-muted-foreground">· {suffix}</span>}
            </span>
            {displayValue === name && <Check className="ml-2 h-4 w-4 text-primary" />}
        </DropdownMenuItem>
    )

    return (
        <>
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

            <DropdownMenu>
                <DropdownMenuTrigger asChild>
                    <Button
                        variant="outline"
                        data-testid="team-selector-trigger"
                        className="cursor-pointer"
                    >
                        <Users className="h-4 w-4" />
                        <span className="text-sm">{displayValue || 'Select team'}</span>
                    </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent
                    align="end"
                    className="w-max min-w-[14rem] max-w-[min(32rem,calc(100vw-2rem))]"
                >
                    {tenantTeamNames.length > 0 && (
                        <>
                            <DropdownMenuLabel className="text-xs text-muted-foreground">
                                Teams
                            </DropdownMenuLabel>
                            {tenantTeamNames.map(name => renderItem(name))}
                        </>
                    )}
                    {userEscalationTeams.length > 0 && (
                        <>
                            {tenantTeamNames.length > 0 && <DropdownMenuSeparator />}
                            <DropdownMenuLabel className="text-xs text-muted-foreground">
                                Escalation Teams
                            </DropdownMenuLabel>
                            {userEscalationTeams.map(t => renderItem(t.name, 'Escalation'))}
                        </>
                    )}
                    {userRoleTeams.length > 0 && (
                        <>
                            {(tenantTeamNames.length > 0 || userEscalationTeams.length > 0) && <DropdownMenuSeparator />}
                            <DropdownMenuLabel className="text-xs text-muted-foreground">
                                Access Roles
                            </DropdownMenuLabel>
                            {userRoleTeams.map(t => renderItem(t.name, tagForTypes(t.types)))}
                        </>
                    )}
                </DropdownMenuContent>
            </DropdownMenu>
            {selectedTeam && !(isLeadership || isSupportEngineer) && (
                <span className="text-xs text-warning italic ml-2">
                    Viewing: {selectedTeam}
                </span>
            )}
        </>
    )
}
