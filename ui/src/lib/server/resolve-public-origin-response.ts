import { resolvePublicOrigin } from "@/lib/server/resolve-public-origin";
import { sanitizeCallbackUrl } from "@/lib/utils/url";
import { NextResponse } from "next/server";

function logNextAuthUrlMisconfiguration(e: unknown): void {
  const msg = e instanceof Error ? e.message : String(e);
  console.error("NEXTAUTH_URL is missing or invalid:", msg);
}

/**
 * Same validation as {@link resolvePublicOrigin}. On failure redirects with **302** to
 * {@code /login?error=configuration} on the request host (HTML) while logging — avoids raw JSON
 * when navigations hit OAuth API routes or the auth proxy.
 */
export function resolvePublicOriginOrConfigurationLoginRedirect(
  requestOrigin: string,
  callbackPathname: string
): { ok: true; origin: string } | { ok: false; response: NextResponse } {
  try {
    return { ok: true, origin: resolvePublicOrigin() };
  } catch (e) {
    logNextAuthUrlMisconfiguration(e);
    const loginUrl = new URL("/login", requestOrigin);
    loginUrl.searchParams.set("error", "configuration");
    loginUrl.searchParams.set("callbackUrl", sanitizeCallbackUrl(callbackPathname));
    return { ok: false, response: NextResponse.redirect(loginUrl, 302) };
  }
}

/**
 * Same as {@link resolvePublicOriginOrConfigurationLoginRedirect} — for {@code /api/oauth/start}
 * and {@code /api/oauth/callback} where the browser uses {@code window.location}.
 */
export function tryResolvePublicOrigin(
  requestOrigin: string,
  callbackPathname: string
): { ok: true; origin: string } | { ok: false; response: NextResponse } {
  return resolvePublicOriginOrConfigurationLoginRedirect(requestOrigin, callbackPathname);
}
