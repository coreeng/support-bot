import { getResolutionTimesByWeek } from '@/db/dashboard'
import { createDashboardRoute } from '@/lib/utils'

// GET /api/db/dashboard/resolution-times-by-week - Fetch resolution times by week
export const GET = createDashboardRoute(async (dateFrom, dateTo) => {
    return await getResolutionTimesByWeek(dateFrom, dateTo)
})
