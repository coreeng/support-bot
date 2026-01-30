'use client'

import { useEffect } from 'react'

// Force dynamic rendering for this page
export const dynamic = 'force-dynamic'

export default function PopupCallback() {
  useEffect(() => {
    if (typeof window !== 'undefined') {
      // Get status from URL parameters
      const urlParams = new URLSearchParams(window.location.search)
      const status = urlParams.get('status')

      if (status === 'success') {
        // Authentication successful
        if (window.opener) {
          window.opener.postMessage({ type: 'LOGIN_SUCCESS' }, '*')
        }
      } else {
        // Authentication failed
        if (window.opener) {
          window.opener.postMessage({ type: 'LOGIN_FAILED' }, '*')
        }
      }

      // Close the popup after a short delay
      setTimeout(() => {
        window.close()
      }, 100)
    }
  }, [])

  return (
    <div className="flex items-center justify-center h-screen bg-gray-50">
      <div className="text-center">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-500 mx-auto mb-4"></div>
        <p className="text-gray-600">Processing authentication...</p>
        <p className="text-sm text-gray-500 mt-2">This window will close automatically...</p>
      </div>
    </div>
  )
}
