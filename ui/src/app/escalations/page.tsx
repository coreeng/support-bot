'use client'

import EscalationsPage from '@/components/escalations/escalations'
import { DashboardLayout } from '@/components/layout/DashboardLayout'

export default function Escalations() {
  return (
    <DashboardLayout>
      <EscalationsPage />
    </DashboardLayout>
  )
}

