'use client'

import { signIn } from 'next-auth/react'
import { useSession } from 'next-auth/react'
import { useRouter } from 'next/navigation'
import { useEffect } from 'react'

// Force dynamic rendering for this page
export const dynamic = 'force-dynamic'

export default function SignInPage() {
  const { data: session, status } = useSession()
  const router = useRouter()

  useEffect(() => {
    if (typeof window !== 'undefined') {
      // Get parameters from URL directly to avoid Suspense boundary requirement
      const urlParams = new URLSearchParams(window.location.search)
      const isPopup = urlParams.get('popup') === 'true'
      const provider = urlParams.get('provider')

      // For popup auth, provider must be specified - no hardcoded fallback
      if (isPopup && !provider) {
        console.error('Popup authentication requires provider to be specified')
        // Close popup and notify parent of error
        if (window.opener) {
          window.opener.postMessage({ type: 'LOGIN_ERROR', error: 'No provider specified' }, '*')
        }
        window.close()
        return
      }

      if (isPopup && status !== 'loading') {
        if (session) {
          // Already authenticated in popup, redirect to callback
          router.push('/api/auth/popup/callback')
        } else {
        // Not authenticated, initiate OAuth flow with popup callback
        const callbackUrl = `${window.location.origin}/api/auth/popup/callback`
        signIn(provider!, {
          callbackUrl
        })
        }
        return
      }

      // Normal signin flow
      if (status === 'loading') return

      if (session) {
        // User is authenticated, redirect to home
        router.push('/')
      } else {
        // User is not authenticated, redirect to home (this page shouldn't be accessed directly)
        router.push('/')
      }
    }
  }, [session, status, router])

  return (
    <div className="flex items-center justify-center h-screen">
      <div className="text-center">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-500 mx-auto mb-4"></div>
        <p className="text-gray-600">Redirecting...</p>
      </div>
    </div>
  )
}
