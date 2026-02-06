import { NextRequest, NextResponse } from 'next/server'

// Proxies /backend/* to the API service inside the cluster, avoiding CORS
// issues with GCP IAP blocking unauthenticated OPTIONS preflights.
//
// next.config.ts rewrites bake environment variables at build time during
// `next build`, so setting BACKEND_URL at runtime has no effect. Using a
// proxy reads BACKEND_URL at runtime instead.
export function proxy(request: NextRequest) {
  const backendUrl = process.env.BACKEND_URL || 'http://localhost:8080'
  const path = request.nextUrl.pathname.replace(/^\/backend/, '')
  const url = new URL(path + request.nextUrl.search, backendUrl)

  return NextResponse.rewrite(url)
}

export const config = {
  matcher: '/backend/:path*',
}
