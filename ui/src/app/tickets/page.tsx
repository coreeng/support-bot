'use client'

import TicketsPage from '@/components/tickets/tickets'
import { DashboardLayout } from '@/components/layout/DashboardLayout'

export default function Tickets() {
  return (
    <DashboardLayout>
      <TicketsPage />
    </DashboardLayout>
  )
}

