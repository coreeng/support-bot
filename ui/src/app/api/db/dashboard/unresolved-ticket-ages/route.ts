import { getUnresolvedTicketAges } from '@/db/dashboard'
import { createDashboardRoute } from '@/lib/utils'

// GET /api/db/dashboard/unresolved-ticket-ages - Fetch unresolved ticket ages
export const GET = createDashboardRoute(async (dateFrom, dateTo) => {
    return await getUnresolvedTicketAges(dateFrom, dateTo)
})
