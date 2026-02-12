import { renderHook, act } from "@testing-library/react";
import { useSession, signOut } from "next-auth/react";
import { useAuth } from "../useAuth";
import {
  createTestUser,
  createTestTeam,
  createEscalationTeam,
  mockLoadingSession,
  mockUnauthenticatedSession,
  mockAuthenticatedSession,
  mockAuthenticatedSessionWithoutUser,
} from "../../test-utils/auth-mocks";

jest.mock("next-auth/react", () => ({
  useSession: jest.fn(),
  signOut: jest.fn(),
}));

const mockUseSession = useSession as jest.MockedFunction<typeof useSession>;
const mockSignOut = signOut as jest.MockedFunction<typeof signOut>;

describe("useAuth", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe("session states", () => {
    it("returns loading state when session is loading", () => {
      mockUseSession.mockReturnValue(mockLoadingSession());

      const { result } = renderHook(() => useAuth());

      expect(result.current.isLoading).toBe(true);
      expect(result.current.isAuthenticated).toBe(false);
      expect(result.current.user).toBeNull();
    });

    it("returns unauthenticated state when session is unauthenticated", () => {
      mockUseSession.mockReturnValue(mockUnauthenticatedSession());

      const { result } = renderHook(() => useAuth());

      expect(result.current.isLoading).toBe(false);
      expect(result.current.isAuthenticated).toBe(false);
      expect(result.current.user).toBeNull();
    });

    it("returns authenticated state with user when session is valid", () => {
      const user = createTestUser({ name: "Jane Doe", email: "jane@example.com" });
      mockUseSession.mockReturnValue(mockAuthenticatedSession(user));

      const { result } = renderHook(() => useAuth());

      expect(result.current.isLoading).toBe(false);
      expect(result.current.isAuthenticated).toBe(true);
      expect(result.current.user).toEqual(user);
    });

    it("returns unauthenticated when status is authenticated but user is missing", () => {
      mockUseSession.mockReturnValue(mockAuthenticatedSessionWithoutUser());

      const { result } = renderHook(() => useAuth());

      expect(result.current.isLoading).toBe(false);
      expect(result.current.isAuthenticated).toBe(false);
      expect(result.current.user).toBeNull();
    });
  });

  describe("role flags", () => {
    it("sets isLeadership to true when user has LEADERSHIP role", () => {
      const user = createTestUser({ roles: ["LEADERSHIP"] });
      mockUseSession.mockReturnValue(mockAuthenticatedSession(user));

      const { result } = renderHook(() => useAuth());

      expect(result.current.isLeadership).toBe(true);
      expect(result.current.isSupportEngineer).toBe(false);
      expect(result.current.isEscalationTeam).toBe(false);
    });

    it("sets isSupportEngineer to true when user has SUPPORT_ENGINEER role", () => {
      const user = createTestUser({ roles: ["SUPPORT_ENGINEER"] });
      mockUseSession.mockReturnValue(mockAuthenticatedSession(user));

      const { result } = renderHook(() => useAuth());

      expect(result.current.isLeadership).toBe(false);
      expect(result.current.isSupportEngineer).toBe(true);
      expect(result.current.isEscalationTeam).toBe(false);
    });

    it("sets isEscalationTeam to true when user has ESCALATION role", () => {
      const user = createTestUser({ roles: ["ESCALATION"] });
      mockUseSession.mockReturnValue(mockAuthenticatedSession(user));

      const { result } = renderHook(() => useAuth());

      expect(result.current.isLeadership).toBe(false);
      expect(result.current.isSupportEngineer).toBe(false);
      expect(result.current.isEscalationTeam).toBe(true);
    });

    it("sets multiple role flags when user has multiple roles", () => {
      const user = createTestUser({ roles: ["LEADERSHIP", "ESCALATION"] });
      mockUseSession.mockReturnValue(mockAuthenticatedSession(user));

      const { result } = renderHook(() => useAuth());

      expect(result.current.isLeadership).toBe(true);
      expect(result.current.isSupportEngineer).toBe(false);
      expect(result.current.isEscalationTeam).toBe(true);
    });

    it("sets all role flags to false when user has no roles", () => {
      const user = createTestUser({ roles: [] });
      mockUseSession.mockReturnValue(mockAuthenticatedSession(user));

      const { result } = renderHook(() => useAuth());

      expect(result.current.isLeadership).toBe(false);
      expect(result.current.isSupportEngineer).toBe(false);
      expect(result.current.isEscalationTeam).toBe(false);
    });

    it("sets all role flags to false when user is null", () => {
      mockUseSession.mockReturnValue(mockUnauthenticatedSession());

      const { result } = renderHook(() => useAuth());

      expect(result.current.isLeadership).toBe(false);
      expect(result.current.isSupportEngineer).toBe(false);
      expect(result.current.isEscalationTeam).toBe(false);
    });
  });

  describe("actualEscalationTeams", () => {
    it("returns empty array when user is null", () => {
      mockUseSession.mockReturnValue(mockUnauthenticatedSession());

      const { result } = renderHook(() => useAuth());

      expect(result.current.actualEscalationTeams).toEqual([]);
    });

    it("returns empty array when user is not on escalation team", () => {
      const user = createTestUser({
        roles: ["SUPPORT_ENGINEER"],
        teams: [createTestTeam({ types: ["support"] })],
      });
      mockUseSession.mockReturnValue(mockAuthenticatedSession(user));

      const { result } = renderHook(() => useAuth());

      expect(result.current.actualEscalationTeams).toEqual([]);
    });

    it("returns team codes for escalation-type teams", () => {
      const user = createTestUser({
        roles: ["ESCALATION"],
        teams: [
          createEscalationTeam({ code: "platform-team", label: "Platform Team" }),
          createEscalationTeam({ code: "infra-team", label: "Infrastructure Team" }),
        ],
      });
      mockUseSession.mockReturnValue(mockAuthenticatedSession(user));

      const { result } = renderHook(() => useAuth());

      expect(result.current.actualEscalationTeams).toEqual([
        "platform-team",
        "infra-team",
      ]);
    });

    it("filters out non-escalation teams", () => {
      const user = createTestUser({
        roles: ["ESCALATION"],
        teams: [
          createEscalationTeam({ code: "escalation-team" }),
          createTestTeam({ code: "support-team", types: ["support"] }),
        ],
      });
      mockUseSession.mockReturnValue(mockAuthenticatedSession(user));

      const { result } = renderHook(() => useAuth());

      expect(result.current.actualEscalationTeams).toEqual(["escalation-team"]);
    });

    it("falls back to label when code is empty", () => {
      const user = createTestUser({
        roles: ["ESCALATION"],
        teams: [createEscalationTeam({ code: "", label: "Fallback Label" })],
      });
      mockUseSession.mockReturnValue(mockAuthenticatedSession(user));

      const { result } = renderHook(() => useAuth());

      expect(result.current.actualEscalationTeams).toEqual(["Fallback Label"]);
    });

    it("matches escalation type case-insensitively", () => {
      const user = createTestUser({
        roles: ["ESCALATION"],
        teams: [
          createTestTeam({ code: "team-a", types: ["ESCALATION"] }),
          createTestTeam({ code: "team-b", types: ["Escalation"] }),
        ],
      });
      mockUseSession.mockReturnValue(mockAuthenticatedSession(user));

      const { result } = renderHook(() => useAuth());

      expect(result.current.actualEscalationTeams).toEqual(["team-a", "team-b"]);
    });
  });

  describe("logout", () => {
    it("calls signOut with /login callback URL", () => {
      const user = createTestUser();
      mockUseSession.mockReturnValue(mockAuthenticatedSession(user));

      const { result } = renderHook(() => useAuth());

      act(() => {
        result.current.logout();
      });

      expect(mockSignOut).toHaveBeenCalledTimes(1);
      expect(mockSignOut).toHaveBeenCalledWith({ callbackUrl: "/login" });
    });

    it("logout is callable even when unauthenticated", () => {
      mockUseSession.mockReturnValue(mockUnauthenticatedSession());

      const { result } = renderHook(() => useAuth());

      act(() => {
        result.current.logout();
      });

      expect(mockSignOut).toHaveBeenCalledWith({ callbackUrl: "/login" });
    });
  });

  describe("return type completeness", () => {
    it("returns all expected properties", () => {
      const user = createTestUser({ roles: ["ESCALATION"] });
      mockUseSession.mockReturnValue(mockAuthenticatedSession(user));

      const { result } = renderHook(() => useAuth());

      expect(result.current).toEqual({
        user: expect.any(Object),
        isLoading: expect.any(Boolean),
        isAuthenticated: expect.any(Boolean),
        isLeadership: expect.any(Boolean),
        isEscalationTeam: expect.any(Boolean),
        isSupportEngineer: expect.any(Boolean),
        actualEscalationTeams: expect.any(Array),
        logout: expect.any(Function),
      });
    });
  });
});
