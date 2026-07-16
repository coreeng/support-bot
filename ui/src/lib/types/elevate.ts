export interface ElevateProduct {
  id: string;
  slug: string;
  name: string;
  customer: string | null;
  createdAt: string;
  lastUpdatedAt: string;
  journeyCount: number;
  userCount: number;
  assignmentCount: number;
}

export interface ElevateUser {
  id: string;
  productId: string;
  name: string;
  description: string | null;
  createdAt: string;
  lastUpdatedAt: string;
  journeyCount: number;
}

export interface ElevateJourney {
  id: string;
  slug: string;
  name: string;
  productId: string;
  productSlug: string;
  userDescription: string | null;
  primaryProblems: string | null;
  createdAt: string;
  lastUpdatedAt: string;
  userCount: number;
  missingUserCount: number;
  crossProductUserCount: number;
}

export interface ElevateEntityCounts {
  products: number;
  journeys: number;
  users: number;
  assignments: number;
}

export interface ElevateIntegrityCounts {
  orphanJourneys: number;
  orphanUsers: number;
  missingAssignments: number;
  crossProductAssignments: number;
}

export interface ElevateStatus {
  configured: boolean;
  baseUrl: string | null;
  statusInterval: string;
  syncInterval: string;
  lastPingAttemptAt: string | null;
  lastPingSuccessAt: string | null;
  lastPingSucceeded: boolean | null;
  lastPingError: string | null;
  lastSyncAttemptAt: string | null;
  lastSyncSuccessAt: string | null;
  lastSyncSucceeded: boolean | null;
  lastSyncError: string | null;
  snapshotVersion: string | null;
  counts: ElevateEntityCounts;
  integrity: ElevateIntegrityCounts;
}

export interface ElevatePage<T> {
  content: T[];
  page: number;
  totalElements: number;
  totalPages: number;
}

export type ElevateRelationshipFilter = "all" | "linked" | "unassigned";
export type ElevateRelationshipSort = "name" | "relationships";

export interface ElevatePageRequest {
  snapshotVersion: string;
  page?: number;
  pageSize?: number;
  query?: string;
  relationship?: ElevateRelationshipFilter;
  sort?: ElevateRelationshipSort;
  direction?: "asc" | "desc";
}

export type ElevateIntegrityIssueType = "orphanJourney" | "orphanUser" | "missingAssignment" | "crossProductAssignment";

export interface ElevateIntegrityIssue {
  type: ElevateIntegrityIssueType;
  journeyId?: string;
  journeyName?: string;
  journeyProductId?: string;
  userId?: string;
  userName?: string;
  userProductId?: string;
}
