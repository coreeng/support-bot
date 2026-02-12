/**
 * Shared test utilities for authentication mocking.
 *
 * Provides type-safe factories for creating test users, teams, and session states.
 * Use these utilities to avoid `as any` casts and maintain consistency across tests.
 */

import type { Session } from "next-auth";
import type { AuthUser, AuthTeam } from "@/auth.config";

/**
 * Creates a test team with sensible defaults.
 */
export function createTestTeam(overrides: Partial<AuthTeam> = {}): AuthTeam {
  return {
    label: "Test Team",
    code: "test-team",
    types: ["support"],
    name: "test-team",
    ...overrides,
  };
}

/**
 * Creates an escalation team (team with escalation type).
 */
export function createEscalationTeam(
  overrides: Partial<AuthTeam> = {}
): AuthTeam {
  return createTestTeam({
    label: "Escalation Team",
    code: "escalation-team",
    types: ["escalation"],
    name: "escalation-team",
    ...overrides,
  });
}

/**
 * Creates a test user with sensible defaults.
 */
export function createTestUser(overrides: Partial<AuthUser> = {}): AuthUser {
  return {
    id: "test-user-id",
    email: "test@example.com",
    name: "Test User",
    teams: [createTestTeam()],
    roles: [],
    ...overrides,
  };
}

/**
 * Session state types for useSession mock.
 */
export type SessionStatus = "loading" | "authenticated" | "unauthenticated";

export interface MockSessionReturn {
  data: Session | null;
  status: SessionStatus;
  update: () => Promise<Session | null>;
}

/**
 * Creates a loading session state.
 */
export function mockLoadingSession(): MockSessionReturn {
  return {
    data: null,
    status: "loading",
    update: jest.fn().mockResolvedValue(null),
  };
}

/**
 * Creates an unauthenticated session state.
 */
export function mockUnauthenticatedSession(): MockSessionReturn {
  return {
    data: null,
    status: "unauthenticated",
    update: jest.fn().mockResolvedValue(null),
  };
}

/**
 * Creates an authenticated session state with the provided user.
 */
export function mockAuthenticatedSession(
  user: AuthUser,
  accessToken = "test-token"
): MockSessionReturn {
  return {
    data: {
      user,
      accessToken,
      expires: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
    },
    status: "authenticated",
    update: jest.fn().mockResolvedValue(null),
  };
}

/**
 * Creates an authenticated session with no user (edge case).
 */
export function mockAuthenticatedSessionWithoutUser(): MockSessionReturn {
  return {
    data: {
      expires: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
    } as Session,
    status: "authenticated",
    update: jest.fn().mockResolvedValue(null),
  };
}
