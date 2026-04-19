/**
 * Browser-facing origin for OAuth redirect_uri and post-OAuth redirects.
 *
 * NEXTAUTH_URL is the single source of truth — it must be set in every environment.
 * We intentionally do NOT fall back to X-Forwarded-Host/Proto because those headers
 * can be spoofed when the app is reachable without a trusted reverse proxy, which
 * would let an attacker influence the redirect_uri sent to the IdP.
 *
 * For JSON/redirect helpers used by OAuth routes and the proxy, import from
 * `./resolve-public-origin-response` (uses `next/server`, kept separate so Jest can test this file
 * without loading Next internals).
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
