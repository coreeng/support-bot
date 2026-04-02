import type {NextRequest} from "next/server";

export type PublicOriginInput = {
  nextAuthUrl?: string | null;
  forwardedHost: string | null;
  forwardedProto: string | null;
  fallbackOrigin: string;
};

/**
 * Browser-facing origin for OAuth redirect_uri and post-OAuth redirects.
 * Pure logic for tests; use {@link resolvePublicOrigin} from route handlers.
 */
export function computePublicOrigin(input: PublicOriginInput): string {
  const nu = input.nextAuthUrl?.trim();
  if (nu) {
    try {
      return new URL(nu).origin;
    } catch {
      /* fall through */
    }
  }
  if (input.forwardedHost) {
    const host = input.forwardedHost.split(",")[0].trim();
    const proto = (input.forwardedProto ?? "https").split(",")[0].trim();
    return `${proto}://${host}`;
  }
  return input.fallbackOrigin;
}

/**
 * Same as {@link computePublicOrigin} using the current request and process env.
 * Behind Kubernetes ingress, {@link NextRequest.nextUrl} can reflect the pod bind address
 * (e.g. https://0.0.0.0:3000) instead of the public URL.
 */
export function resolvePublicOrigin(request: NextRequest): string {
  return computePublicOrigin({
    nextAuthUrl: process.env.NEXTAUTH_URL,
    forwardedHost: request.headers.get("x-forwarded-host"),
    forwardedProto: request.headers.get("x-forwarded-proto"),
    fallbackOrigin: request.nextUrl.origin,
  });
}
