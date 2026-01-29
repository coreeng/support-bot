import { createDashboardRoute } from '@/lib/utils/api-handler'
import { getIncomingVsResolvedRate } from '@/db/dashboard'

export const GET = createDashboardRoute(getIncomingVsResolvedRate)

