import type { NextRequest } from "next/server";
import { errorResponse } from "./backend-fetch";

/**
 * Double-submit CSRF check: the X-CSRF-Token header must match the NextAuth CSRF cookie.
 * Returns a 403 response when the check fails, or null when the request passes.
 */
export function validateCsrfToken(request: NextRequest): Response | null {
  const csrfTokenFromHeader = request.headers.get("X-CSRF-Token");
  const csrfCookieName = process.env.NODE_ENV === "production" ? "__Host-authjs.csrf-token" : "authjs.csrf-token";
  const csrfCookieValue = request.cookies.get(csrfCookieName)?.value;

  if (!csrfTokenFromHeader || !csrfCookieValue) {
    return errorResponse("Missing CSRF token", 403);
  }

  // NextAuth CSRF token format: "token|hash"
  const cookieToken = csrfCookieValue.split("|")[0];

  if (csrfTokenFromHeader !== cookieToken) {
    return errorResponse("Invalid CSRF token", 403);
  }

  return null;
}
