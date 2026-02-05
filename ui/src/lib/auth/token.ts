const TOKEN_KEY = 'auth_token'
// NEXT_PUBLIC_ env vars are inlined at build time, so a single Docker image
// can't serve multiple environments. Instead, derive the API URL at runtime
// by stripping '-ui' from the hostname (e.g. support-bot-ui.foo.com -> support-bot.foo.com).
export function getApiUrl(hostname?: string, protocol?: string): string {
  const currentHostname = hostname ?? (typeof window !== 'undefined' ? window.location.hostname : undefined)
  const currentProtocol = protocol ?? (typeof window !== 'undefined' ? window.location.protocol : undefined)

  if (!currentHostname || currentHostname === 'localhost') {
    return process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080'
  }

  const apiHostname = currentHostname.replace('-ui', '')
  return `${currentProtocol}//${apiHostname}`
}

export const API_URL = getApiUrl()

export interface AuthTeam {
  label: string
  code: string
  types: string[]
  name: string  // Alias for label, used throughout the UI
}

export interface AuthUser {
  email: string
  name: string
  teams: AuthTeam[]
  isLeadership: boolean
  isSupportEngineer: boolean
  isEscalation: boolean
}

export function getToken(): string | null {
  if (typeof window === 'undefined') return null
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  if (typeof window === 'undefined') return
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken(): void {
  if (typeof window === 'undefined') return
  localStorage.removeItem(TOKEN_KEY)
}

export function isAuthenticated(): boolean {
  const token = getToken()
  if (!token) return false

  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    const expiry = payload.exp * 1000
    return Date.now() < expiry
  } catch {
    return false
  }
}

export function getTokenExpiry(): Date | null {
  const token = getToken()
  if (!token) return null

  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return new Date(payload.exp * 1000)
  } catch {
    return null
  }
}

export async function exchangeCodeForToken(code: string): Promise<string> {
  const response = await fetch(`${API_URL}/auth/token`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ code }),
  })

  if (!response.ok) {
    throw new Error('Failed to exchange code for token')
  }

  const data = await response.json()
  setToken(data.token)
  return data.token
}

interface ApiTeamResponse {
  label: string
  code: string
  types: string[]
}

interface ApiUserResponse {
  email: string
  name: string
  teams: ApiTeamResponse[]
  isLeadership: boolean
  isSupportEngineer: boolean
  isEscalation: boolean
}

export async function fetchCurrentUser(): Promise<AuthUser | null> {
  const token = getToken()
  if (!token) return null

  try {
    const response = await fetch(`${API_URL}/auth/me`, {
      headers: {
        'Authorization': `Bearer ${token}`,
      },
    })

    if (!response.ok) {
      if (response.status === 401) {
        clearToken()
      }
      return null
    }

    const data: ApiUserResponse = await response.json()

    // Map teams to include `name` alias for `label`
    return {
      ...data,
      teams: data.teams.map(team => ({
        ...team,
        name: team.label,
      })),
    }
  } catch {
    return null
  }
}

export async function logout(): Promise<void> {
  const token = getToken()
  if (token) {
    try {
      await fetch(`${API_URL}/auth/logout`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
        },
      })
    } catch {
      // Ignore logout errors
    }
  }
  clearToken()
}

export function getLoginUrl(provider: 'google' | 'azure'): string {
  return `${API_URL}/oauth2/authorization/${provider}`
}
