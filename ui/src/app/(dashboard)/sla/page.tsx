"use client";

import { RequireDashboardAccess } from "@/components/AccessDenied";
import DashboardsPage from "@/components/dashboards/dashboards";

export default function SLA() {
  return (
    <RequireDashboardAccess>
      <DashboardsPage />
    </RequireDashboardAccess>
  );
}
