import { auth } from "@/auth";
import { NextResponse } from "next/server";

/**
 * Next.js 16 proxy (formerly middleware) for route protection.
 * Redirects unauthenticated users to /login for protected routes.
 */
export const proxy = auth((req) => {
  const { nextUrl, auth: session } = req;
  const isLoggedIn = !!session?.user;

  // Public routes that don't require authentication
  const publicPaths = ["/login", "/api/auth", "/api/health"];
  const isPublicPath = publicPaths.some((path) =>
    nextUrl.pathname.startsWith(path)
  );

  if (isPublicPath) {
    return NextResponse.next();
  }

  // Redirect unauthenticated users to login
  if (!isLoggedIn) {
    const loginUrl = new URL("/login", nextUrl.origin);
    loginUrl.searchParams.set("callbackUrl", nextUrl.pathname);
    return NextResponse.redirect(loginUrl);
  }

  return NextResponse.next();
});

export const config = {
  matcher: ["/((?!_next/static|_next/image|.*\\..+$).*)"],
};
