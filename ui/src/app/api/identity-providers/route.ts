 import { NextResponse } from "next/server";
import { publicFetch } from "../_lib/public-fetch";

export const dynamic = "force-dynamic";
export const revalidate = 0;

const NO_CACHE_HEADERS = { "Cache-Control": "no-store" } as const;
const EMPTY_PROVIDERS = { providers: [] };

/**
 * Fetch available OAuth2 providers from backend.
 * Returns empty array when backend is unreachable or returns non-ok response.
 * This endpoint is public (unauthenticated) since it's needed on the login page.
 */
export async function GET() {
  try {
    const response = await publicFetch("/auth/providers", { cache: "no-store" });
    if (!response.ok) {
      console.error(
        `[IdentityProviders] Backend returned HTTP ${response.status}. Falling back to empty providers list.`
      );
      return NextResponse.json(EMPTY_PROVIDERS, { headers: NO_CACHE_HEADERS });
    }

    const body = await response.json();
    return NextResponse.json(body, { headers: NO_CACHE_HEADERS });
  } catch (error) {
    console.error(
      "[IdentityProviders] Failed to fetch providers from backend. " +
      "Falling back to empty list. Error:",
      error
    );
    return NextResponse.json(EMPTY_PROVIDERS, { headers: NO_CACHE_HEADERS });
  }
}
