import { NextRequest, NextResponse } from "next/server";
import { publicFetch } from "../../../_lib/public-fetch";

export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ provider: string }> }
) {
  const { provider } = await params;

  if (provider !== "google" && provider !== "azure") {
    return NextResponse.json({ error: "Invalid provider" }, { status: 400 });
  }

  // Get the callbackUrl from query params (where user should return after login)
  const userCallbackUrl = request.nextUrl.searchParams.get("callbackUrl") || "/";

  // Build the OAuth callback URL for this UI (where OAuth provider redirects back)
  const oauthCallbackUrl = new URL(
    `/api/auth/callback/${provider}`,
    process.env.NEXTAUTH_URL
  ).toString();

  // Get OAuth URL from backend (server-to-server, no auth required)
  const urlParams = new URLSearchParams({
    provider,
    redirectUri: oauthCallbackUrl
  });
  const response = await publicFetch(`/auth/oauth-url?${urlParams.toString()}`);

  if (!response.ok) {
    console.error("Failed to get OAuth URL:", response.status);
    return NextResponse.json(
      { error: "Failed to get OAuth URL" },
      { status: 500 }
    );
  }

  const result = await response.json();

  // Store the user's desired callback URL in a cookie so we can retrieve it after OAuth
  const redirectResponse = NextResponse.redirect(result.url);
  redirectResponse.cookies.set("oauth-callback-url", userCallbackUrl, {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    maxAge: 600, // 10 minutes - enough time to complete OAuth
    path: "/",
  });

  return redirectResponse;
}
