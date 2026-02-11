'use client'
import { createContext, useContext, ReactNode, useState, useMemo } from 'react'
import { useAuth } from '@/hooks/useAuth'

type TeamFilterContextType = {
    selectedTeam: string | null  // null = "All Teams", string = specific team
    setSelectedTeam: (team: string | null) => void
    effectiveTeams: string[]  // The teams to filter by (either all teams or selected team)
    hasFullAccess: boolean  // true if should see all features (Leadership viewing All Teams)
    allTeams: string[]  // All available teams for the user
    initialized: boolean  // Whether the context has finished initializing
}

const TeamFilterContext = createContext<TeamFilterContextType | undefined>(undefined)

export const TeamFilterProvider = ({ children }: { children: ReactNode }) => {
    const { user, isLeadership, isSupportEngineer } = useAuth()

    // Track if we've done one-time initialization
    const [hasInitialized, setHasInitialized] = useState(false)

    // Initialize selectedTeam lazily based on user
    const [selectedTeam, setSelectedTeam] = useState<string | null>(null)

    // Derive initialized state
    const initialized = hasInitialized || !!user

    // One-time initialization during render when user becomes available
    if (user && !hasInitialized) {
        setHasInitialized(true)
        if (selectedTeam === null && user.teams.length > 0) {
            setSelectedTeam(user.teams[0].name)
        }
    }

    // Helper: Check if a team is a pure role group by types (leadership/support)
    const isPureRoleGroup = (team: { name: string; types?: string[] } | null) => {
        if (!team) return false
        const types = team.types || []
        return types.some(t => /leadership/i.test(t) || /support/i.test(t))
    }

    // Determine which teams to filter by
    const effectiveTeams = useMemo(() => {
        if (!user) return []

        // Filter out pure role groups (leadership/support) by types; keep escalation & tenant teams
        const dataTeams = user.teams
            .filter(t => !(t.types || []).some(type => /leadership/i.test(type) || /support/i.test(type)))
            .map(t => t.name)

        // If selected team is a pure role group, return empty array to indicate "view all"
        // This signals to consuming components that no team filtering should be applied
        const selected = user.teams.find(t => t.name === selectedTeam)
        if (selected && isPureRoleGroup(selected)) {
            // Return empty array - consumers should interpret as "no filter" when hasFullAccess is true
            return []
        }

        // If Leadership/SupportEngineer with no selection or "All Teams"
        if ((isLeadership || isSupportEngineer) && (selectedTeam === null || selectedTeam === 'all')) {
            // Return empty array to indicate "view all"
            return []
        }

        // Viewing a specific team (including escalation/tenant)
        if (selectedTeam) {
            return [selectedTeam]
        }

        // Default: all user's data teams (or empty if none)
        return dataTeams
    }, [user, isLeadership, isSupportEngineer, selectedTeam])

    // Full access rules:
    // - True if selected team is a role team (leadership/support) OR no team selected (all) AND user is leadership/support
    // - False if selected team is escalation
    // - False when selecting non-role tenant teams (even if user has leadership/support roles)
    const hasFullAccess = useMemo(() => {
        const selected = user?.teams.find(t => t.name === selectedTeam) || null
        const selectedTypes = selected?.types || []
        const selectedIsRole = selectedTypes.some(type => /leadership/i.test(type) || /support/i.test(type))
        const selectedIsEscalation = selectedTypes.some(type => /escalation/i.test(type))

        if (selectedIsEscalation) return false
        if (selectedIsRole) return true

        // No team selected ("all") and user has leadership/support roles
        if ((selectedTeam === null || selectedTeam === 'all' || selectedTeam === undefined) && (isLeadership || isSupportEngineer)) {
            return true
        }

        return false
    }, [isLeadership, isSupportEngineer, selectedTeam, user])

    // Get all available teams for the user
    const allTeams = useMemo(() => {
        if (!user) return []
        return user.teams
            .filter(t => !(t.types || []).some(type => /leadership/i.test(type) || /support/i.test(type)))
            .map(t => t.name)
    }, [user])

    return (
        <TeamFilterContext.Provider value={{
            selectedTeam,
            setSelectedTeam,
            effectiveTeams,
            hasFullAccess,
            allTeams,
            initialized,
        }}>
            {children}
        </TeamFilterContext.Provider>
    )
}

export const useTeamFilter = () => {
    const context = useContext(TeamFilterContext)
    if (!context) throw new Error('useTeamFilter must be used within a TeamFilterProvider')
    return context
}

