import { getTicketResolutionDurationDistribution } from '@/db/dashboard'
import { createDashboardRoute } from '@/lib/utils'

// GET /api/db/dashboard/resolution-duration-distribution - Fetch ticket resolution duration distribution
export const GET = createDashboardRoute(async (dateFrom, dateTo) => {
    return await getTicketResolutionDurationDistribution(dateFrom, dateTo)
})
