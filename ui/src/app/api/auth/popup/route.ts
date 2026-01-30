import { NextRequest, NextResponse } from 'next/server'
import { getServerSession } from 'next-auth/next'
import { authOptions } from '@/lib/auth'

export async function GET(request: NextRequest) {
  const { searchParams } = new URL(request.url)
  const provider = searchParams.get('provider') || 'azure-ad'

  try {
    // Check if already authenticated
    const session = await getServerSession(authOptions)
    if (session) {
      // Already authenticated, redirect to success callback
      return NextResponse.redirect(new URL('/api/auth/popup/callback', request.url))
    }

    // Get the base URL for constructing callback URLs
    // For iframe contexts, NEXTAUTH_URL should be properly set in production
    const baseUrl = process.env.NEXTAUTH_URL || `https://${request.headers.get('host')}`

    console.log(`[Popup Auth] Using baseUrl: ${baseUrl}, NEXTAUTH_URL: ${process.env.NEXTAUTH_URL}, Host: ${request.headers.get('host')}, Iframe: ${request.headers.get('x-iframe-request')}`)

    // For popup auth, redirect to the main signin page but indicate it's a popup
    const signInUrl = new URL(`${baseUrl}/auth/signin`)
    signInUrl.searchParams.set('popup', 'true')
    signInUrl.searchParams.set('provider', provider)

    return NextResponse.redirect(signInUrl)
  } catch (error) {
    console.error('Popup auth route error:', error)
    return NextResponse.redirect(new URL('/api/auth/popup/callback?error=auth_failed', request.url))
  }
}
