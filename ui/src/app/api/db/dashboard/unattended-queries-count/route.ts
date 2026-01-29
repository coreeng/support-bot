import { getUnattendedQueriesCount } from '@/db/dashboard'
import { createDashboardRoute } from '@/lib/utils'

// GET /api/db/dashboard/unattended-queries-count - Fetch unattended queries count
export const GET = createDashboardRoute(async (dateFrom, dateTo) => {
    return await getUnattendedQueriesCount(dateFrom, dateTo)
})
