import { getFirstResponseDurationDistribution } from '@/db/dashboard'
import { createDashboardRoute } from '@/lib/utils'

// GET /api/db/dashboard/first-response-distribution - Fetch first response duration distribution
export const GET = createDashboardRoute(async (dateFrom, dateTo) => {
    return await getFirstResponseDurationDistribution(dateFrom, dateTo)
})
