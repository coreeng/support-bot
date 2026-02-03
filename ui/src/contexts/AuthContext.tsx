'use client'
import { createContext, useContext, ReactNode, useState, useEffect, useCallback, useMemo } from 'react'
import {
  AuthUser,
  getToken,
  isAuthenticated as checkAuth,
  fetchCurrentUser,
  logout as authLogout,
  clearToken,
} from '@/lib/auth/token'

type AuthContextType = {
  user: AuthUser | null
  isLoading: boolean
  isAuthenticated: boolean
  isLeadership: boolean
  isEscalationTeam: boolean
  isSupportEngineer: boolean
  actualEscalationTeams: string[]
  logout: () => Promise<void>
  refreshUser: () => Promise<void>
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [user, setUser] = useState<AuthUser | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  const loadUser = useCallback(async () => {
    if (!checkAuth()) {
      setUser(null)
      setIsLoading(false)
      return
    }

    try {
      const userData = await fetchCurrentUser()
      setUser(userData)
    } catch {
      clearToken()
      setUser(null)
    } finally {
      setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    loadUser()
  }, [loadUser])

  const logout = useCallback(async () => {
    await authLogout()
    setUser(null)
  }, [])

  const refreshUser = useCallback(async () => {
    setIsLoading(true)
    await loadUser()
  }, [loadUser])

  const isAuthenticated = !!user && checkAuth()
  const isLeadership = user?.isLeadership ?? false
  const isSupportEngineer = user?.isSupportEngineer ?? false
  const isEscalationTeam = user?.isEscalation ?? false

  const actualEscalationTeams = useMemo(() => {
    if (!user || !isEscalationTeam) return []
    return user.teams
      .filter(t => t.types.some(type => /escalation/i.test(type)))
      .map(t => t.code || t.label)
  }, [user, isEscalationTeam])

  return (
    <AuthContext.Provider
      value={{
        user,
        isLoading,
        isAuthenticated,
        isLeadership,
        isEscalationTeam,
        isSupportEngineer,
        actualEscalationTeams,
        logout,
        refreshUser,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export const useAuth = () => {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used within an AuthProvider')
  return context
}

// Backward compatibility alias
export const useUser = useAuth
