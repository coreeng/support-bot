'use client'

import KnowledgeGapsPage from '@/components/knowledgegaps/knowledge-gaps'
import { RequireDashboardAccess } from '@/components/AccessDenied'

export default function KnowledgeGaps() {
  return <RequireDashboardAccess><KnowledgeGapsPage /></RequireDashboardAccess>
}

