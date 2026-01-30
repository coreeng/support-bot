import { NextRequest, NextResponse } from 'next/server'
import { getServerSession } from 'next-auth/next'
import { authOptions } from '@/lib/auth'

export async function GET(request: NextRequest) {
  try {
    // Get the session to verify authentication was successful
    const session = await getServerSession(authOptions)

    // Use NEXTAUTH_URL for iframe contexts to avoid localhost redirects
    const baseUrl = process.env.NEXTAUTH_URL || new URL(request.url).origin

    console.log(`[Popup Callback] Using baseUrl: ${baseUrl}, NEXTAUTH_URL: ${process.env.NEXTAUTH_URL}, Request origin: ${new URL(request.url).origin}, Iframe: ${request.headers.get('x-iframe-request')}`)

    if (!session) {
      // Authentication failed, redirect to popup callback page with failure status
      return NextResponse.redirect(`${baseUrl}/popup-callback?status=failed`)
    }

    // Authentication successful, redirect to popup callback page with success status
    return NextResponse.redirect(`${baseUrl}/popup-callback?status=success`)
  } catch (error) {
    console.error('Popup callback error:', error)
    // Use NEXTAUTH_URL for iframe contexts to avoid localhost redirects
    const baseUrl = process.env.NEXTAUTH_URL || new URL(request.url).origin
    return NextResponse.redirect(`${baseUrl}/popup-callback?status=error`)
  }
}
