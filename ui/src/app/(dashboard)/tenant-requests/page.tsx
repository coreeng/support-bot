"use client";

import { RequireDashboardAccess } from "@/components/AccessDenied";
import TenantRequestsPage from "@/components/tenant-requests/tenant-requests";

export default function TenantRequests() {
  return (
    <RequireDashboardAccess>
      <TenantRequestsPage />
    </RequireDashboardAccess>
  );
}
