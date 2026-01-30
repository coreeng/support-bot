// src/lib/utils/api-handler.ts
import { NextRequest, NextResponse } from 'next/server'
import { extractDateParams } from './query-params'
import { rateLimit, RATE_LIMITS } from '@/lib/rate-limit'

/**
 * Creates a standardized API route handler with date parameter extraction, rate limiting, and error handling
 * @param handler - Async function that processes the request and returns data
 * @returns Next.js route handler function
 * @example
 * export const GET = createDashboardRoute(async (dateFrom, dateTo) => {
 *     return await getMyData(dateFrom, dateTo)
 * })
 */
export function createDashboardRoute<T>(
    handler: (dateFrom?: string, dateTo?: string) => Promise<T>
) {
    return async (request: NextRequest) => {
        try {
            // Apply rate limiting for dashboard endpoints
            const rateLimitResult = await rateLimit(request, RATE_LIMITS.DASHBOARD)
            if (!rateLimitResult.allowed) {
                return NextResponse.json(
                    { 
                        error: 'Too Many Requests',
                        message: 'Rate limit exceeded. Please try again later.',
                        retryAfter: Math.ceil((rateLimitResult.reset - Date.now()) / 1000)
                    },
                    { 
                        status: 429,
                        headers: {
                            'X-RateLimit-Limit': rateLimitResult.limit.toString(),
                            'X-RateLimit-Remaining': rateLimitResult.remaining.toString(),
                            'X-RateLimit-Reset': rateLimitResult.reset.toString(),
                            'Retry-After': Math.ceil((rateLimitResult.reset - Date.now()) / 1000).toString()
                        }
                    }
                )
            }

            const { dateFrom, dateTo } = extractDateParams(request)
            const data = await handler(dateFrom, dateTo)
            
            // Add rate limit headers to successful responses
            return NextResponse.json(data, {
                headers: {
                    'X-RateLimit-Limit': rateLimitResult.limit.toString(),
                    'X-RateLimit-Remaining': rateLimitResult.remaining.toString(),
                    'X-RateLimit-Reset': rateLimitResult.reset.toString()
                }
            })
        } catch (err) {
            console.error('Dashboard API Error:', err)
            return NextResponse.json(
                { error: 'Failed to fetch data' },
                { status: 500 }
            )
        }
    }
}

/**
 * Creates a standardized API route handler for endpoints without date parameters, with rate limiting
 * @param handler - Async function that processes the request and returns data
 * @returns Next.js route handler function
 * @example
 * export const GET = createSimpleRoute(async () => {
 *     return await getMyData()
 * })
 */
export function createSimpleRoute<T>(handler: () => Promise<T>) {
    return async (request: NextRequest) => {
        try {
            // Apply rate limiting for simple API endpoints
            const rateLimitResult = await rateLimit(request, RATE_LIMITS.API)
            if (!rateLimitResult.allowed) {
                return NextResponse.json(
                    { 
                        error: 'Too Many Requests',
                        message: 'Rate limit exceeded. Please try again later.',
                        retryAfter: Math.ceil((rateLimitResult.reset - Date.now()) / 1000)
                    },
                    { 
                        status: 429,
                        headers: {
                            'X-RateLimit-Limit': rateLimitResult.limit.toString(),
                            'X-RateLimit-Remaining': rateLimitResult.remaining.toString(),
                            'X-RateLimit-Reset': rateLimitResult.reset.toString(),
                            'Retry-After': Math.ceil((rateLimitResult.reset - Date.now()) / 1000).toString()
                        }
                    }
                )
            }

            const data = await handler()
            
            // Add rate limit headers to successful responses
            return NextResponse.json(data, {
                headers: {
                    'X-RateLimit-Limit': rateLimitResult.limit.toString(),
                    'X-RateLimit-Remaining': rateLimitResult.remaining.toString(),
                    'X-RateLimit-Reset': rateLimitResult.reset.toString()
                }
            })
        } catch (err) {
            console.error('API Error:', err)
            return NextResponse.json(
                { error: 'Failed to fetch data' },
                { status: 500 }
            )
        }
    }
}

