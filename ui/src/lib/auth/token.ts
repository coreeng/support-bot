const TOKEN_KEY = 'auth_token'
const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080'

export interface AuthUser {
  email: string
  name: string
  teams: Array<{
    label: string
    code: string
    types: string[]
  }>
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

    return response.json()
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
