import { getEscalationsByImpact } from '@/db/dashboard'
import { createDashboardRoute } from '@/lib/utils'

// GET /api/db/dashboard/escalations-by-impact - Fetch escalations by impact level
export const GET = createDashboardRoute(async (dateFrom, dateTo) => {
    return await getEscalationsByImpact(dateFrom, dateTo)
})
