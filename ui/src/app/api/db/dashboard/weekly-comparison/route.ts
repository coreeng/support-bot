import { getWeeklyComparison } from '@/db/dashboard'
import { createSimpleRoute } from '@/lib/utils'

// GET /api/db/dashboard/weekly-comparison - Fetch weekly comparison metrics
export const GET = createSimpleRoute(async () => {
    return await getWeeklyComparison()
})
