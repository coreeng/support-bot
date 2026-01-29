import { getEscalationPercentageByTag } from '@/db/dashboard'
import { createDashboardRoute } from '@/lib/utils'

// GET /api/db/dashboard/escalation-percentage-by-tag - Fetch escalation percentage by tag
export const GET = createDashboardRoute(async (dateFrom, dateTo) => {
    return await getEscalationPercentageByTag(dateFrom, dateTo)
})
