import "next-auth"
import "next-auth/jwt"

// Backend team structure
export interface PlatformTeam {
  name: string
  types?: string[]
  groupRefs: string[]
}

declare module "next-auth" {
  /**
   * Returned by `useSession`, `getSession` and received as a prop on the `SessionProvider` React Context
   */
  interface Session {
    user: {
      email: string
      name?: string | null
      teams: PlatformTeam[]  // Array of teams
      isLeadership: boolean  // Computed from backend API
      isEscalation: boolean  // Computed from backend API
      isSupportEngineer: boolean  // Computed from backend API
    }
  }

  interface Profile {
    email?: string
    name?: string
  }
}

declare module "next-auth/jwt" {
  /** Returned by the `jwt` callback and `getToken`, when using JWT sessions */
  interface JWT {
    email: string
    name?: string | null
    minTeams: Array<{ n: string, t: string[] }>  // Minified teams: n=name, t=types (no groupRefs)
    isLeadership: boolean  // Computed from backend API
    isEscalation: boolean  // Computed from backend API
    isSupportEngineer: boolean  // Computed from backend API
  }
}

