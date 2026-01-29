import { getEscalationsByTeam } from '@/db/dashboard'
import { createDashboardRoute } from '@/lib/utils'

// GET /api/db/dashboard/escalations-by-team - Fetch escalations by team
export const GET = createDashboardRoute(async (dateFrom, dateTo) => {
    return await getEscalationsByTeam(dateFrom, dateTo)
})
