'use client'

import HealthPage from '@/components/health/health'
import { RequireDashboardAccess } from '@/components/AccessDenied'

export default function Health() {
  return <RequireDashboardAccess><HealthPage /></RequireDashboardAccess>
}

