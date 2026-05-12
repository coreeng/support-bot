"use client";

import { RequireDashboardAccess } from "@/components/AccessDenied";
import HealthPage from "@/components/health/health";

export default function Health() {
  return (
    <RequireDashboardAccess>
      <HealthPage />
    </RequireDashboardAccess>
  );
}
