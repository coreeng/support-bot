"use client";

import { RequireDashboardAccess } from "@/components/AccessDenied";
import DocumentationAnalysisPage from "@/components/documentation-analysis/documentation-analysis";

export default function DocumentationAnalysis() {
  return (
    <RequireDashboardAccess>
      <DocumentationAnalysisPage />
    </RequireDashboardAccess>
  );
}
