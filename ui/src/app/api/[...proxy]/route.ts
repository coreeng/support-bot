import { NextRequest, NextResponse } from 'next/server';
import { rateLimit, RATE_LIMITS } from '@/lib/rate-limit';
import { authenticateProxyRequest } from './auth';

// Read BACKEND_URL from environment
const BACKEND_URL = process.env.BACKEND_URL || 'http://localhost:8080';

/**
 * Proxy handler for all /api/* requests (except /api/db and /api/auth)
 * This runs in the Node.js runtime (not Edge), so it has full network access
 */
async function proxyHandler(request: NextRequest, context: { params: Promise<{ proxy: string[] }> }) {
  // Await params (required in Next.js 15+)
  const { proxy } = await context.params;
  const path = proxy.join('/');

  // Authenticate the request (handles both production and test mode)
  const token = await authenticateProxyRequest(request);

  if (!token || !token.email) {
    console.warn(`[API Proxy] Unauthorized access attempt to /api/${path}`);
    return NextResponse.json(
      { error: 'Unauthorized', message: 'Authentication required' },
      { status: 401 }
    );
  }

  // AUTHORIZATION: Restrict dashboard analytics to leadership/support engineers only
  // All other endpoints are accessible to authenticated users
  if (path.startsWith('db/dashboard')) {
    const hasAccess = token.isLeadership || token.isSupportEngineer

    if (!hasAccess) {
      console.warn(
        `[API Proxy] Forbidden: User ${token.email} attempted to access dashboard endpoint /api/${path}`
      )
      return NextResponse.json(
        {
          error: 'Forbidden',
          message: 'Dashboard analytics are restricted to leadership and support engineers.'
        },
        { status: 403 }
      )
    }
  }

  // Apply rate limiting (per user email if authenticated, else per IP)
  const rateLimitResult = await rateLimit(request, RATE_LIMITS.API);
  if (!rateLimitResult.allowed) {
    const retryAfter = Math.max(1, Math.ceil((rateLimitResult.reset - Date.now()) / 1000));
    return NextResponse.json(
      {
        error: 'Too Many Requests',
        message: 'Rate limit exceeded. Please try again later.',
        retryAfter,
      },
      {
        status: 429,
        headers: {
          'X-RateLimit-Limit': rateLimitResult.limit.toString(),
          'X-RateLimit-Remaining': rateLimitResult.remaining.toString(),
          'X-RateLimit-Reset': rateLimitResult.reset.toString(),
          'Retry-After': retryAfter.toString(),
        },
      }
    );
  }

  // Skip NextAuth routes - they handle their own logic
  if (path.startsWith('auth/')) {
    return NextResponse.next();
  }

  // Handle all other API routes through the proxy (including db/ routes)

  const searchParams = request.nextUrl.searchParams.toString();
  const fullPath = searchParams ? `/${path}?${searchParams}` : `/${path}`;
  const backendUrl = `${BACKEND_URL}${fullPath}`;

  try {
    // Build clean headers (exclude hop-by-hop headers)
    const headers: Record<string, string> = {};
    request.headers.forEach((value, key) => {
      const lowerKey = key.toLowerCase();
      // Skip hop-by-hop headers
      if (lowerKey !== 'connection' &&
        lowerKey !== 'keep-alive' &&
        lowerKey !== 'transfer-encoding' &&
        lowerKey !== 'upgrade' &&
        lowerKey !== 'host') {
        headers[key] = value;
      }
    });

    // Forward the request to the backend
    const response = await fetch(backendUrl, {
      method: request.method,
      headers,
      body: request.method !== 'GET' && request.method !== 'HEAD' ? await request.text() : undefined,
      cache: 'no-store',
    });

    console.log(`[API Proxy] ${request.method} ${fullPath} → ${response.status} ${response.statusText}`);

    // Clone response headers and add rate limit metadata
    const responseHeaders = new Headers(response.headers);
    responseHeaders.set('X-RateLimit-Limit', rateLimitResult.limit.toString());
    responseHeaders.set('X-RateLimit-Remaining', rateLimitResult.remaining.toString());
    responseHeaders.set('X-RateLimit-Reset', rateLimitResult.reset.toString());

    // Stream the response directly without reading into memory
    // This is faster and uses less memory for large responses
    return new NextResponse(response.body, {
      status: response.status,
      statusText: response.statusText,
      headers: responseHeaders,
    });
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : 'Unknown error';
    const errorCause = error instanceof Error ? error.cause : undefined;

    console.error(`[API Proxy] Error proxying ${fullPath}:`, {
      message: errorMessage,
      cause: errorCause,
    });

    return NextResponse.json(
      { error: 'Backend service unavailable', details: errorMessage },
      { status: 503 }
    );
  }
}

// Export all HTTP methods
export const GET = proxyHandler;
export const POST = proxyHandler;
export const PUT = proxyHandler;
export const DELETE = proxyHandler;
export const PATCH = proxyHandler;
export const OPTIONS = proxyHandler;

// Force this route to use the Node.js runtime (not Edge)
export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

