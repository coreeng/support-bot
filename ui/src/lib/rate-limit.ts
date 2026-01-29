/**
 * Simple in-memory rate limiter for API routes
 * For production, consider using Redis or a dedicated rate limiting service
 */

import { NextRequest } from 'next/server'
import { getToken } from 'next-auth/jwt'

interface RateLimitEntry {
  count: number
  resetAt: number
}

// In-memory store for rate limit data
// Key format: "email:endpoint" or "ip:endpoint"
const limitStore = new Map<string, RateLimitEntry>()

// Clean up expired entries every 5 minutes
setInterval(() => {
  const now = Date.now()
  for (const [key, entry] of limitStore.entries()) {
    if (entry.resetAt < now) {
      limitStore.delete(key)
    }
  }
}, 5 * 60 * 1000)

export interface RateLimitConfig {
  /**
   * Time window in milliseconds (default: 60000 = 1 minute)
   */
  windowMs?: number
  /**
   * Maximum number of requests per window (default: 100)
   */
  max?: number
  /**
   * Identifier for this rate limit (default: 'default')
   */
  id?: string
}

export interface RateLimitResult {
  allowed: boolean
  limit: number
  remaining: number
  reset: number
}

/**
 * Check if request should be rate limited
 * @param identifier - Unique identifier (email or IP)
 * @param config - Rate limit configuration
 * @returns Rate limit result with allowed status and metadata
 */
export function checkRateLimit(
  identifier: string,
  config: RateLimitConfig = {}
): RateLimitResult {
  const {
    windowMs = 60 * 1000, // 1 minute default
    max = 100, // 100 requests per minute default
    id = 'default',
  } = config

  const key = `${identifier}:${id}`
  const now = Date.now()
  const entry = limitStore.get(key)

  // No entry or expired entry - create new
  if (!entry || entry.resetAt < now) {
    limitStore.set(key, {
      count: 1,
      resetAt: now + windowMs,
    })
    return {
      allowed: true,
      limit: max,
      remaining: max - 1,
      reset: now + windowMs,
    }
  }

  // Entry exists and not expired - increment count
  entry.count++

  // Check if limit exceeded
  if (entry.count > max) {
    return {
      allowed: false,
      limit: max,
      remaining: 0,
      reset: entry.resetAt,
    }
  }

  return {
    allowed: true,
    limit: max,
    remaining: max - entry.count,
    reset: entry.resetAt,
  }
}

/**
 * Rate limit configurations for different endpoint types
 */
export const RATE_LIMITS = {
  // Strict limit for authentication endpoints
  AUTH: {
    windowMs: 15 * 60 * 1000, // 15 minutes
    max: 5, // 5 attempts per 15 minutes
    id: 'auth',
  },
  // Standard limit for API endpoints
  API: {
    windowMs: 60 * 1000, // 1 minute
    max: 100, // 100 requests per minute
    id: 'api',
  },
  // Stricter limit for expensive operations (dashboards, reports)
  DASHBOARD: {
    windowMs: 60 * 1000, // 1 minute
    max: 40, // 30 requests per minute
    id: 'dashboard',
  },
} as const

/**
 * Extract a unique identifier from the request for rate limiting
 * Uses user email if authenticated, otherwise falls back to IP address
 */
async function getIdentifier(request: NextRequest): Promise<string> {
  // Try to get user from JWT token
  const token = await getToken({ req: request, secret: process.env.NEXTAUTH_SECRET })
  if (token?.email) {
    return token.email as string
  }

  // Fallback to IP address
  const forwarded = request.headers.get('x-forwarded-for')
  const ip = forwarded ? forwarded.split(',')[0].trim() : request.headers.get('x-real-ip') || 'unknown'
  return ip
}

/**
 * Apply rate limiting to a Next.js API request
 * @param request - Next.js request object
 * @param config - Rate limit configuration
 * @returns Rate limit result
 */
export async function rateLimit(
  request: NextRequest,
  config: RateLimitConfig = {}
): Promise<RateLimitResult> {
  const identifier = await getIdentifier(request)
  return checkRateLimit(identifier, config)
}

