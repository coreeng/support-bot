"use client";

import { RequireDashboardAccess } from "@/components/AccessDenied";
import SupportSummaryPage from "@/components/summary/support-summary";

export default function Summary() {
  return (
    <RequireDashboardAccess>
      <SupportSummaryPage />
    </RequireDashboardAccess>
  );
}
