import { getWeeklyTicketCounts } from '@/db/dashboard'
import { createSimpleRoute } from '@/lib/utils'

// GET /api/db/dashboard/weekly-ticket-counts - Fetch weekly ticket counts
export const GET = createSimpleRoute(async () => {
    return await getWeeklyTicketCounts()
})
