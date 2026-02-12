import { NextRequest, NextResponse } from "next/server";
import { publicFetch } from "../../../_lib/public-fetch";

export async function GET(request: NextRequest) {
  const searchParams = request.nextUrl.searchParams;
  const code = searchParams.get("code");
  const error = searchParams.get("error");

  if (error) {
    const loginUrl = new URL("/login", process.env.NEXTAUTH_URL);
    loginUrl.searchParams.set("error", error);
    return NextResponse.redirect(loginUrl);
  }

  if (!code) {
    const loginUrl = new URL("/login", process.env.NEXTAUTH_URL);
    loginUrl.searchParams.set("error", "No authorization code received");
    return NextResponse.redirect(loginUrl);
  }

  const callbackUrl = new URL(
    "/api/auth/callback/google",
    process.env.NEXTAUTH_URL
  ).toString();

  const response = await publicFetch("/auth/oauth/exchange", {
    method: "POST",
    body: JSON.stringify({ provider: "google", code, redirectUri: callbackUrl }),
  });

  if (!response.ok) {
    console.error("OAuth code exchange failed:", response.status);
    const loginUrl = new URL("/login", process.env.NEXTAUTH_URL);
    loginUrl.searchParams.set("error", "Token exchange failed");
    return NextResponse.redirect(loginUrl);
  }

  const result = await response.json();

  const loginUrl = new URL("/login", process.env.NEXTAUTH_URL);
  loginUrl.searchParams.set("token", result.token);
  return NextResponse.redirect(loginUrl);
}
