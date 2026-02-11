"use client";

import { signOut, useSession } from "next-auth/react";
import { useMemo } from "react";
import type { AuthTeam, AuthUser } from "@/auth.config";

interface UseAuthReturn {
  user: AuthUser | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  isLeadership: boolean;
  isEscalationTeam: boolean;
  isSupportEngineer: boolean;
  actualEscalationTeams: string[];
  logout: () => void;
}

export function useAuth(): UseAuthReturn {
  const { data: session, status } = useSession();

  const isLoading = status === "loading";
  const isAuthenticated = status === "authenticated" && !!session?.user;
  const user = session?.user ?? null;

  const isLeadership = user?.roles?.includes("LEADERSHIP") ?? false;
  const isSupportEngineer = user?.roles?.includes("SUPPORT_ENGINEER") ?? false;
  const isEscalationTeam = user?.roles?.includes("ESCALATION") ?? false;

  const actualEscalationTeams = useMemo(() => {
    if (!user || !isEscalationTeam) return [];
    return user.teams
      .filter((t: AuthTeam) => t.types.some((type: string) => /escalation/i.test(type)))
      .map((t: AuthTeam) => t.code || t.label);
  }, [user, isEscalationTeam]);

  const logout = () => {
    signOut({ callbackUrl: "/login" });
  };

  return {
    user,
    isLoading,
    isAuthenticated,
    isLeadership,
    isEscalationTeam,
    isSupportEngineer,
    actualEscalationTeams,
    logout,
  };
}
