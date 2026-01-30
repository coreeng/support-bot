import { getTopEscalatedTagsThisWeek } from '@/db/dashboard'
import { createSimpleRoute } from '@/lib/utils'

// GET /api/db/dashboard/top-escalated-tags-this-week - Fetch top escalated tags this week
export const GET = createSimpleRoute(async () => {
    return await getTopEscalatedTagsThisWeek()
})
