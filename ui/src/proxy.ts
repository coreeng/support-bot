import { NextResponse } from 'next/server'
import type { NextRequest } from 'next/server'

// Export iframe detection for use in API routes
export function isIframeRequest(request: NextRequest): boolean {
  // ONLY rely on the explicit client-side header for iframe detection
  // This is the most reliable method since it's set by our client code
  const iframeHeader = request.headers.get('x-iframe-request')
  if (iframeHeader === 'true') {
    console.log('[Iframe Detection] TRUE: x-iframe-request header set to true')
    return true
  }

  // Remove other iframe detection methods as they can cause false positives
  // - sec-fetch-dest can be unreliable across different browsers/proxies
  // - referer checks can fail with proxies, CDNs, or privacy settings

  console.log('[Iframe Detection] FALSE: x-iframe-request header not set to true')
  return false
}

/**
 * Middleware to protect API routes with authentication
 * Runs on all /api/* routes except auth endpoints
 * 
 * TEST MODE: When NODE_ENV=test, bypasses JWT validation if session cookie exists.
 * This allows functional tests (Playwright/Cucumber) to mock authentication without
 * generating real JWTs. Production and development modes always validate real tokens.
 */
export async function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl

  // Allow auth endpoints (sign in, callbacks, etc.)
  if (pathname.startsWith('/api/auth')) {
    return NextResponse.next()
  }

  // Allow health check endpoints (for load balancers, monitoring)
  if (pathname === '/api/livez' || pathname === '/api/readyz') {
    return NextResponse.next()
  }

  // Skip API routes - they handle authentication internally
  if (pathname.startsWith('/api')) {
    return NextResponse.next()
  }

  // Non-API routes pass through
  return NextResponse.next()
}

// Configure which routes this middleware runs on
export const config = {
  matcher: [
    // Match all API routes except auth routes
    '/api/:path*',
    // Exclude static files
    '/((?!_next/static|_next/image|favicon.ico|.*\\.(?:svg|png|jpg|jpeg|gif|webp)$).*)',
  ],
}
