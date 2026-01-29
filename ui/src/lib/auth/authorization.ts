/**
 * Server-side authorization utilities
 * Used in API routes to check user permissions
 */

import { getToken } from 'next-auth/jwt'
import { NextRequest } from 'next/server'

export interface AuthToken {
  email: string
  name?: string | null
  teams?: Array<{ name: string; types?: string[]; groupRefs?: string[] }>
  isLeadership: boolean
  isEscalation: boolean
  isSupportEngineer: boolean
}

/**
 * Get authenticated user from request
 * Returns null if not authenticated
 */
export async function getAuthUser(req: NextRequest): Promise<AuthToken | null> {
  try {
    const token = await getToken({
      req,
      secret: process.env.NEXTAUTH_SECRET,
    })

    if (!token || !token.email) {
      return null
    }

    // Rehydrate minified team data from JWT to full format
    const minTeams = (token.minTeams as Array<{ n: string, t: string[] }>) || []
    const teams = minTeams.map(mt => ({
      name: mt.n,
      types: mt.t || [],
      groupRefs: [], // Explicitly empty to satisfy interface
    }))

    return {
      email: token.email as string,
      name: token.name as string | null,
      teams: teams,
      isLeadership: (token.isLeadership as boolean) || false,
      isEscalation: (token.isEscalation as boolean) || false,
      isSupportEngineer: (token.isSupportEngineer as boolean) || false,
    }
  } catch (error) {
    console.error('[Auth] Error getting user from token:', error)
    return null
  }
}

/**
 * Check if user has leadership access
 */
export function requireLeadership(user: AuthToken | null): boolean {
  return user?.isLeadership || false
}

/**
 * Check if user has escalation team access
 */
export function requireEscalation(user: AuthToken | null): boolean {
  return user?.isEscalation || false
}

/**
 * Check if user has support engineer access
 */
export function requireSupportEngineer(user: AuthToken | null): boolean {
  return user?.isSupportEngineer || false
}

/**
 * Check if user is member of specific team
 */
export function isMemberOfTeam(user: AuthToken | null, teamName: string): boolean {
  if (!user || !user.teams) return false
  return user.teams.some(team => team.name === teamName)
}

/**
 * Get user's team names
 */
export function getUserTeams(user: AuthToken | null): string[] {
  if (!user || !user.teams) return []
  return user.teams.map(team => team.name)
}

