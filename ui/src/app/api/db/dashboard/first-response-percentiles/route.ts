import { getFirstResponsePercentiles } from '@/db/dashboard'
import { createDashboardRoute } from '@/lib/utils'

// GET /api/db/dashboard/first-response-percentiles - Fetch first response percentiles
// Example of using the new utility function to reduce boilerplate
export const GET = createDashboardRoute(async (dateFrom, dateTo) => {
    return await getFirstResponsePercentiles(dateFrom, dateTo)
})
