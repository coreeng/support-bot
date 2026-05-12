"use client";

import { RequireDashboardAccess } from "@/components/AccessDenied";
import KnowledgeGapsPage from "@/components/knowledgegaps/knowledge-gaps";

export default function KnowledgeGaps() {
  return (
    <RequireDashboardAccess>
      <KnowledgeGapsPage />
    </RequireDashboardAccess>
  );
}
