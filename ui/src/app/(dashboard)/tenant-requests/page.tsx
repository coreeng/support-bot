'use client'

import TenantRequestsPage from '@/components/tenant-requests/tenant-requests'
import { RequireDashboardAccess } from '@/components/AccessDenied'

export default function TenantRequests() {
  return <RequireDashboardAccess><TenantRequestsPage /></RequireDashboardAccess>
}
