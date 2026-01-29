import { getAvgEscalationDurationByTag } from '@/db/dashboard'
import { createDashboardRoute } from '@/lib/utils'

// GET /api/db/dashboard/avg-escalation-duration-by-tag - Fetch average escalation duration by tag
export const GET = createDashboardRoute(async (dateFrom, dateTo) => {
    return await getAvgEscalationDurationByTag(dateFrom, dateTo)
})
