// Mock for auth.config in tests
import type { NextAuthConfig } from "next-auth";

export interface AuthTeam {
  label: string;
  code: string;
  types: string[];
  name: string;
}

export interface AuthUser {
  id: string;
  email: string;
  name: string;
  teams: AuthTeam[];
  roles: string[];
}

export const authConfig: NextAuthConfig = {
  providers: [],
  callbacks: {},
  pages: {
    signIn: "/login",
    error: "/login",
  },
  session: {
    strategy: "jwt",
  },
  trustHost: true,
};
