import { auth } from "@/auth";
import type { NextRequest } from "next/server";
import { NextResponse } from "next/server";

/**
 * Next.js 16 proxy (formerly middleware) for route protection.
 * Redirects unauthenticated users to /login for protected routes.
 */
const publicPaths = ["/login", "/api/auth", "/api/health", "/api/identity-providers"];

const protectedProxy = auth((req) => {
  const { nextUrl, auth: session } = req;
  const isLoggedIn = !!session?.user;
  const { pathname } = nextUrl;

  // Redirect unauthenticated users to login
  if (!isLoggedIn) {
    const loginUrl = new URL("/login", nextUrl.origin);
    loginUrl.searchParams.set("callbackUrl", pathname);
    return NextResponse.redirect(loginUrl);
  }

  return NextResponse.next();
});

export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // Health check endpoints are always public — no auth required.
  if (pathname === "/livez" || pathname === "/readyz") {
    return NextResponse.next();
  }

  // Public routes that don't require authentication.
  if (publicPaths.some((path) => pathname.startsWith(path))) {
    return NextResponse.next();
  }

  // Test bypass: lets Playwright functional tests skip server-side
  // JWE validation, which test tooling cannot satisfy without knowing AUTH_SECRET.
  // Gated behind an explicit env var so it is never active in real deployments.
  if (process.env.E2E_AUTH_BYPASS === "true") {
    const bypass = request.cookies.get("__e2e_auth_bypass");
    if (bypass?.value === "functional-test") {
      return NextResponse.next();
    }
  }

  // NextAuth's `auth` wrapper has overloaded signatures; in proxy context
  // we only pass the request object.
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  return (protectedProxy as any)(request);
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|.*\\..+$).*)"],
};
