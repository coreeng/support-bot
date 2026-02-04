'use client'

import { useEffect, useState, useRef } from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import { exchangeCodeForToken } from '@/lib/auth/token'
import { useAuth } from '@/contexts/AuthContext'

// Track exchanged codes outside component to survive React Strict Mode remounts
const exchangedCodes = new Set<string>()

export default function AuthCallbackPage() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const { refreshUser } = useAuth()
  const [error, setError] = useState<string | null>(null)
  const refreshUserRef = useRef(refreshUser)
  refreshUserRef.current = refreshUser

  useEffect(() => {
    const code = searchParams.get('code')

    if (!code) {
      setError('No authorization code received')
      return
    }

    // Prevent double execution - check if this specific code was already exchanged
    if (exchangedCodes.has(code)) return
    exchangedCodes.add(code)

    exchangeCodeForToken(code)
      .then(async () => {
        // Refresh auth context to load the new user
        await refreshUserRef.current()
        // Redirect to home or the page they were trying to access
        const returnTo = sessionStorage.getItem('auth_return_to') || '/'
        sessionStorage.removeItem('auth_return_to')
        router.replace(returnTo)
      })
      .catch((err) => {
        console.error('Token exchange failed:', err)
        // Remove from set so user can retry with a new code
        exchangedCodes.delete(code)
        setError('Authentication failed. Please try again.')
      })
  }, [searchParams, router])

  if (error) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center">
          <h1 className="text-2xl font-bold text-red-600 mb-4">Authentication Error</h1>
          <p className="text-gray-600 mb-4">{error}</p>
          <a
            href="/login"
            className="text-blue-600 hover:underline"
          >
            Try again
          </a>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen flex items-center justify-center">
      <div className="text-center">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-gray-900 mx-auto mb-4"></div>
        <p className="text-gray-600">Completing authentication...</p>
      </div>
    </div>
  )
}
