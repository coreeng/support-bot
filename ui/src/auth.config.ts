import type { NextAuthConfig } from "next-auth";
import Credentials from "next-auth/providers/credentials";

const BACKEND_URL = process.env.BACKEND_URL!;

/**
 * Exchange auth code for token (public endpoint, no auth required).
 */
async function exchangeCodeForToken(
  code: string
): Promise<{ token: string } | null> {
  const response = await fetch(`${BACKEND_URL}/auth/token`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ code }),
  });

  if (!response.ok) {
    return null;
  }

  return response.json();
}

/**
 * Fetch user data with token.
 */
async function fetchUserWithToken(
  token: string
): Promise<Record<string, unknown> | null> {
  const response = await fetch(`${BACKEND_URL}/auth/me`, {
    headers: { Authorization: `Bearer ${token}` },
  });

  if (!response.ok) {
    return null;
  }

  return response.json();
}

/**
 * NextAuth configuration for backend-driven OAuth2 authentication.
 *
 * Flow:
 * 1. User clicks login → redirects to backend OAuth endpoint
 * 2. Backend handles OAuth with Google/Azure
 * 3. Backend generates JWT and redirects with auth code
 * 4. NextAuth callback exchanges code for JWT via /auth/token
 * 5. User data fetched from /auth/me and stored in session
 */


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

declare module "next-auth" {
  interface Session {
    user: AuthUser;
    accessToken: string;
  }

  interface User extends AuthUser {
    accessToken: string;
  }
}

declare module "@auth/core/jwt" {
  interface JWT {
    accessToken: string;
    email: string;
    name: string;
    teams: AuthTeam[];
    roles: string[];
  }
}

export const authConfig: NextAuthConfig = {
  providers: [
    Credentials({
      id: "backend-oauth",
      name: "Backend OAuth",
      credentials: {
        code: { label: "Auth Code", type: "text" },
      },
      async authorize(credentials) {
        const code = credentials?.code as string;
        if (!code) return null;

        try {
          // Exchange code for JWT token using API layer
          const tokenResult = await exchangeCodeForToken(code);
          if (!tokenResult) {
            console.error("Token exchange failed");
            return null;
          }

          // Fetch user data using API layer
          const userData = await fetchUserWithToken(tokenResult.token);
          if (!userData) {
            console.error("User fetch failed");
            return null;
          }

          return {
            id: userData.email as string,
            email: userData.email as string,
            name: userData.name as string,
            teams: (userData.teams as Array<{ label: string; code: string; types: string[] }>).map((t) => ({
              ...t,
              name: t.code || t.label,
            })),
            roles: userData.roles as string[],
            accessToken: tokenResult.token,
          };
        } catch (error) {
          console.error("Authorization error:", error);
          return null;
        }
      },
    }),
    Credentials({
      id: "backend-token",
      name: "Backend Token",
      credentials: {
        token: { label: "Token", type: "text" },
      },
      async authorize(credentials) {
        const token = credentials?.token as string;
        if (!token) return null;

        try {
          // Token is already a valid backend JWT, just fetch user data
          const userData = await fetchUserWithToken(token);
          if (!userData) {
            console.error("User fetch failed");
            return null;
          }

          return {
            id: userData.email as string,
            email: userData.email as string,
            name: userData.name as string,
            teams: (userData.teams as Array<{ label: string; code: string; types: string[] }>).map((t) => ({
              ...t,
              name: t.code || t.label,
            })),
            roles: userData.roles as string[],
            accessToken: token,
          };
        } catch (error) {
          console.error("Authorization error:", error);
          return null;
        }
      },
    }),
  ],

  callbacks: {
    async jwt({ token, user }) {
      // Initial sign-in: transfer user data to token
      if (user) {
        token.accessToken = user.accessToken as string;
        token.email = user.email!;
        token.name = user.name!;
        token.teams = user.teams as AuthTeam[];
        token.roles = user.roles as string[];
      }
      return token;
    },

    async session({ session, token }) {
      session.accessToken = token.accessToken as string;
      if (session.user) {
        session.user.id = token.email as string;
        session.user.email = token.email as string;
        session.user.name = token.name as string;
        session.user.teams = token.teams as AuthTeam[];
        session.user.roles = token.roles as string[];
      }
      return session;
    },
  },

  pages: {
    signIn: "/login",
    error: "/login",
  },

  session: {
    strategy: "jwt",
    maxAge: 24 * 60 * 60, // 24 hours (match backend JWT expiry)
  },

  trustHost: true,
};
