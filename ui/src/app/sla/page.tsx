'use client'

import DashboardsPage from '@/components/dashboards/dashboards'
import { DashboardLayout } from '@/components/layout/DashboardLayout'

export default function SLA() {
  return (
    <DashboardLayout>
      <DashboardsPage />
    </DashboardLayout>
  )
}

