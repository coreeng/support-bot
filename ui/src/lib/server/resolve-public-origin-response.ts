import { NextResponse } from "next/server";
import { sanitizeCallbackUrl } from "@/lib/utils/url";
import { resolvePublicOrigin } from "@/lib/server/resolve-public-origin";

const NEXTAUTH_URL_MISCONFIG_BODY = {
  error: "Server misconfiguration: NEXTAUTH_URL is required",
} as const;

function logNextAuthUrlMisconfiguration(e: unknown): void {
  const msg = e instanceof Error ? e.message : String(e);
  console.error("NEXTAUTH_URL is missing or invalid:", msg);
}

/**
 * Same origin rules as {@link resolvePublicOrigin}, but returns a JSON 500 response instead of
 * throwing — for API routes (OAuth start/callback).
 */
export function tryResolvePublicOrigin():
  | { ok: true; origin: string }
  | { ok: false; response: NextResponse } {
  try {
    return { ok: true, origin: resolvePublicOrigin() };
  } catch (e) {
    logNextAuthUrlMisconfiguration(e);
    return {
      ok: false,
      response: NextResponse.json(NEXTAUTH_URL_MISCONFIG_BODY, { status: 500 }),
    };
  }
}

/**
 * For the auth proxy: same validation as {@link resolvePublicOrigin}, but on failure redirects to
 * {@code /login?error=configuration} on the request host (HTML) while logging — avoids raw JSON on
 * protected page navigations. OAuth API routes should use {@link tryResolvePublicOrigin} instead.
 */
export function resolvePublicOriginOrConfigurationLoginRedirect(
  requestOrigin: string,
  callbackPathname: string
):
  | { ok: true; origin: string }
  | { ok: false; response: NextResponse } {
  try {
    return { ok: true, origin: resolvePublicOrigin() };
  } catch (e) {
    logNextAuthUrlMisconfiguration(e);
    const loginUrl = new URL("/login", requestOrigin);
    loginUrl.searchParams.set("error", "configuration");
    loginUrl.searchParams.set("callbackUrl", sanitizeCallbackUrl(callbackPathname));
    return { ok: false, response: NextResponse.redirect(loginUrl) };
  }
}
