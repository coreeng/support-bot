import { NextResponse } from "next/server";

/**
 * Browser-facing origin for OAuth redirect_uri and post-OAuth redirects.
 *
 * NEXTAUTH_URL is the single source of truth — it must be set in every environment.
 * We intentionally do NOT fall back to X-Forwarded-Host/Proto because those headers
 * can be spoofed when the app is reachable without a trusted reverse proxy, which
 * would let an attacker influence the redirect_uri sent to the IdP.
 */
export function resolvePublicOrigin(): string {
  const nu = process.env.NEXTAUTH_URL?.trim();
  if (nu) {
    try {
      return new URL(nu).origin;
    } catch {
      /* fall through */
    }
  }
  throw new Error(
    "NEXTAUTH_URL is not set or invalid — required for OAuth redirect_uri"
  );
}

const NEXTAUTH_URL_MISCONFIG_BODY = {
  error: "Server misconfiguration: NEXTAUTH_URL is required",
} as const;

/**
 * Same origin rules as {@link resolvePublicOrigin}, but returns a JSON 500 response instead of
 * throwing so callers (proxy, OAuth routes) behave consistently when NEXTAUTH_URL is missing.
 */
export function tryResolvePublicOrigin():
  | { ok: true; origin: string }
  | { ok: false; response: NextResponse } {
  try {
    return { ok: true, origin: resolvePublicOrigin() };
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    console.error("NEXTAUTH_URL is missing or invalid:", msg);
    return {
      ok: false,
      response: NextResponse.json(NEXTAUTH_URL_MISCONFIG_BODY, { status: 500 }),
    };
  }
}
