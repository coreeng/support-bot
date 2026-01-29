import { getResolutionTimeByTag } from '@/db/dashboard'
import { createDashboardRoute } from '@/lib/utils'

// GET /api/db/dashboard/resolution-time-by-tag - Fetch resolution time by tag (P50/P90)
export const GET = createDashboardRoute(async (dateFrom, dateTo) => {
    return await getResolutionTimeByTag(dateFrom, dateTo)
})
