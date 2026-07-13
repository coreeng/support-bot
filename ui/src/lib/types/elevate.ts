export interface ElevateProduct {
  id: string;
  slug: string;
  name: string;
  customer: string | null;
  createdAt: string;
  lastUpdatedAt: string;
}

export interface ElevateUser {
  id: string;
  productId: string;
  name: string;
  description: string | null;
  createdAt: string;
  lastUpdatedAt: string;
}

export interface ElevateJourney {
  id: string;
  slug: string;
  name: string;
  productId: string;
  productSlug: string;
  userDescription: string | null;
  primaryProblems: string | null;
  userIds: string[];
  createdAt: string;
  lastUpdatedAt: string;
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
  products: ElevateProduct[];
  journeys: ElevateJourney[];
  users: ElevateUser[];
}
