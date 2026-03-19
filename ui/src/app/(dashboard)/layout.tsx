'use client'

import { DashboardLayout } from '@/components/layout/DashboardLayout'

/**
 * Shared layout for all dashboard pages.
 * This layout persists across navigation within the (dashboard) route group,
 * preserving sidebar state (collapsed/expanded) and other layout-level state.
 */
export default function Layout({ children }: { children: React.ReactNode }) {
  return <DashboardLayout>{children}</DashboardLayout>
}

