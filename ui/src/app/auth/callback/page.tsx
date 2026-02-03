'use client'

import { useEffect, useState } from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import { exchangeCodeForToken } from '@/lib/auth/token'

export default function AuthCallbackPage() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const code = searchParams.get('code')

    if (!code) {
      setError('No authorization code received')
      return
    }

    exchangeCodeForToken(code)
      .then(() => {
        // Redirect to home or the page they were trying to access
        const returnTo = sessionStorage.getItem('auth_return_to') || '/'
        sessionStorage.removeItem('auth_return_to')
        router.replace(returnTo)
      })
      .catch((err) => {
        console.error('Token exchange failed:', err)
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
