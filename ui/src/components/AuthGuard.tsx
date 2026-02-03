'use client'

import { useEffect } from 'react'
import { useRouter, usePathname } from 'next/navigation'
import { useAuth } from '@/contexts/AuthContext'

interface AuthGuardProps {
  children: React.ReactNode
  requireAuth?: boolean
}

// Routes that don't require authentication
const PUBLIC_ROUTES = ['/login', '/auth/callback']

export function AuthGuard({ children, requireAuth = true }: AuthGuardProps) {
  const { isAuthenticated, isLoading } = useAuth()
  const router = useRouter()
  const pathname = usePathname()

  useEffect(() => {
    if (isLoading) return

    const isPublicRoute = PUBLIC_ROUTES.some(route => pathname.startsWith(route))

    if (requireAuth && !isAuthenticated && !isPublicRoute) {
      // Store current path for redirect after login
      sessionStorage.setItem('auth_return_to', pathname)
      router.replace('/login')
    }
  }, [isAuthenticated, isLoading, pathname, requireAuth, router])

  // Show loading state while checking auth
  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-gray-900"></div>
      </div>
    )
  }

  // Don't render protected content if not authenticated
  const isPublicRoute = PUBLIC_ROUTES.some(route => pathname.startsWith(route))
  if (requireAuth && !isAuthenticated && !isPublicRoute) {
    return null
  }

  return <>{children}</>
}
