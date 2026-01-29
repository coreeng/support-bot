import { getEscalationTrendsByDate } from '@/db/dashboard'
import { createDashboardRoute } from '@/lib/utils'

// GET /api/db/dashboard/escalation-trends-by-date - Fetch escalation trends by date
export const GET = createDashboardRoute(async (dateFrom, dateTo) => {
    return await getEscalationTrendsByDate(dateFrom, dateTo)
})
