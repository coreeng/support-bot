'use client'
import { createContext, useContext, ReactNode, useMemo } from 'react'
import { useSession } from 'next-auth/react'
import { useEscalationTeams } from '@/lib/hooks/backend'

export type PlatformTeam = {
    name: string
    types?: string[]
    groupRefs: string[]
}

type User = {
    email: string
    teams: PlatformTeam[]  // Array of teams
}

type UserContextType = {
    user: User | null
    isLoading: boolean
    isAuthenticated: boolean
    isLeadership: boolean  // Helper: is user in Leadership team?
    isEscalationTeam: boolean  // Helper: is user in Escalations team?
    isSupportEngineer: boolean  // Helper: is user in Support Engineers team?
    actualEscalationTeams: string[]  // User's actual escalation teams (matched from nested groups)
    isLoadingEscalationTeams: boolean  // Loading state for escalation teams fetch
}

const UserContext = createContext<UserContextType | undefined>(undefined)

export const UserProvider = ({ children }: { children: ReactNode }) => {
    const { data: session, status } = useSession()

    // Transform NextAuth session into our User type
    const user = session?.user ? {
        email: session.user.email,
        teams: session.user.teams || [],
    } : null

    // Get group membership flags from session (computed server-side using environment variables)
    const isLeadership = session?.user?.isLeadership || false
    const isEscalationTeam = session?.user?.isEscalation || false
    const isSupportEngineer = session?.user?.isSupportEngineer || false

    // Fetch all escalation teams from backend (only when authenticated)
    const { data: escalationTeams, isLoading: isLoadingEscalationTeams } = useEscalationTeams(status === 'authenticated')

    // Match user's teams with L2 support teams to find their actual escalation teams
    const actualEscalationTeams = useMemo(() => {
        if (!session?.user?.teams || !isEscalationTeam || !escalationTeams) {
            return []
        }

        // Get all escalation team names
        const l2TeamNames = escalationTeams.map(team => team.name)

        // Cross-reference: find which escalation teams the user is actually a member of
        const userTeamNames = session.user.teams.map(t => t.name)

        const matchedTeams = userTeamNames.filter(userTeam =>
            l2TeamNames.includes(userTeam)
        )

        return matchedTeams
    }, [session, isEscalationTeam, escalationTeams])

    return (
        <UserContext.Provider value={{
            user,
            isLoading: status === 'loading',
            isAuthenticated: status === 'authenticated',
            isLeadership,
            isEscalationTeam,
            isSupportEngineer,
            actualEscalationTeams,
            isLoadingEscalationTeams,
        }}>
            {children}
        </UserContext.Provider>
    )
}

export const useUser = () => {
    const context = useContext(UserContext)
    if (!context) throw new Error('useUser must be used within a UserProvider')
    return context
}
