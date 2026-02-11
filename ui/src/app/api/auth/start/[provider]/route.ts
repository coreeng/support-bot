import { NextRequest, NextResponse } from "next/server";
import { publicFetch } from "../../../_lib/public-fetch";

export async function GET(
  _request: NextRequest,
  { params }: { params: Promise<{ provider: string }> }
) {
  const { provider } = await params;

  if (provider !== "google" && provider !== "azure") {
    return NextResponse.json({ error: "Invalid provider" }, { status: 400 });
  }

  // Build the callback URL for this UI
  const callbackUrl = new URL(
    `/api/auth/callback/${provider}`,
    process.env.NEXTAUTH_URL
  ).toString();

  // Get OAuth URL from backend (server-to-server, no auth required)
  const urlParams = new URLSearchParams({ provider, redirectUri: callbackUrl });
  const response = await publicFetch(`/auth/oauth-url?${urlParams.toString()}`);

  if (!response.ok) {
    console.error("Failed to get OAuth URL:", response.status);
    return NextResponse.json(
      { error: "Failed to get OAuth URL" },
      { status: 500 }
    );
  }

  const result = await response.json();
  return NextResponse.redirect(result.url);
}
