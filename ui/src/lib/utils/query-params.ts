// src/lib/utils/query-params.ts

/**
 * Builds a query string from date parameters
 * @param dateFrom - Optional start date (YYYY-MM-DD format)
 * @param dateTo - Optional end date (YYYY-MM-DD format)
 * @returns Query string with leading '?' if params exist, empty string otherwise
 * @example
 * buildDateQuery('2025-01-01', '2025-01-31') // "?dateFrom=2025-01-01&dateTo=2025-01-31"
 * buildDateQuery() // ""
 */
export function buildDateQuery(dateFrom?: string, dateTo?: string): string {
    const params = new URLSearchParams()
    if (dateFrom) params.append('dateFrom', dateFrom)
    if (dateTo) params.append('dateTo', dateTo)
    return params.toString() ? `?${params.toString()}` : ''
}

/**
 * Extracts date parameters from a NextRequest
 * @param request - NextRequest object
 * @returns Object with optional dateFrom and dateTo strings
 * @example
 * const { dateFrom, dateTo } = extractDateParams(request)
 */
export function extractDateParams(request: Request): { dateFrom?: string; dateTo?: string } {
    const { searchParams } = new URL(request.url)
    return {
        dateFrom: searchParams.get('dateFrom') || undefined,
        dateTo: searchParams.get('dateTo') || undefined,
    }
}

