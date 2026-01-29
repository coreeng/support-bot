import { NextRequest, NextResponse } from 'next/server'
import { getServerSession } from 'next-auth/next'
import { authOptions } from '@/lib/auth'

export async function GET(request: NextRequest) {
  try {
    const session = await getServerSession(authOptions)

    if (!session) {
      // Not authenticated, redirect to home
      return NextResponse.redirect(new URL('/', request.url))
    }

    // User is authenticated, check if this is a popup flow
    const { searchParams } = new URL(request.url)
    const callbackUrl = searchParams.get('callbackUrl')

    if (callbackUrl && callbackUrl.includes('/api/auth/popup/callback')) {
      // This is a popup flow, redirect to the popup callback
      return NextResponse.redirect(new URL(callbackUrl, request.url))
    }

    // Normal flow, redirect to home
    return NextResponse.redirect(new URL('/', request.url))
  } catch (error) {
    console.error('Popup redirect error:', error)
    return NextResponse.redirect(new URL('/', request.url))
  }
}
