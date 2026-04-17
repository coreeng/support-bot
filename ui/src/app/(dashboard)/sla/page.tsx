'use client'

import DashboardsPage from '@/components/dashboards/dashboards'
import { RequireDashboardAccess } from '@/components/AccessDenied'

export default function SLA() {
  return <RequireDashboardAccess><DashboardsPage /></RequireDashboardAccess>
}

