import { getTicketResolutionPercentiles } from '@/db/dashboard'
import { createDashboardRoute } from '@/lib/utils'

// GET /api/db/dashboard/resolution-percentiles - Fetch ticket resolution percentiles
export const GET = createDashboardRoute(async (dateFrom, dateTo) => {
    return await getTicketResolutionPercentiles(dateFrom, dateTo)
})
