import { NextRequest, NextResponse } from "next/server";
import { publicFetch } from "../../../_lib/public-fetch";

export async function GET(request: NextRequest) {
  const searchParams = request.nextUrl.searchParams;
  const code = searchParams.get("code");
  const error = searchParams.get("error");

  // Extract the user's desired callback URL from cookie
  const userCallbackUrl = request.cookies.get("oauth-callback-url")?.value || "/";

  if (error) {
    const loginUrl = new URL("/login", process.env.NEXTAUTH_URL);
    loginUrl.searchParams.set("error", error);
    if (userCallbackUrl !== "/") {
      loginUrl.searchParams.set("callbackUrl", userCallbackUrl);
    }
    const response = NextResponse.redirect(loginUrl);
    // Clear the cookie (must specify path to match the cookie that was set)
    response.cookies.set("oauth-callback-url", "", { path: "/", maxAge: 0 });
    return response;
  }

  if (!code) {
    const loginUrl = new URL("/login", process.env.NEXTAUTH_URL);
    loginUrl.searchParams.set("error", "No authorization code received");
    if (userCallbackUrl !== "/") {
      loginUrl.searchParams.set("callbackUrl", userCallbackUrl);
    }
    const response = NextResponse.redirect(loginUrl);
    // Clear the cookie (must specify path to match the cookie that was set)
    response.cookies.set("oauth-callback-url", "", { path: "/", maxAge: 0 });
    return response;
  }

  const callbackUrl = new URL(
    "/api/auth/callback/azure",
    process.env.NEXTAUTH_URL
  ).toString();

  try {
    const response = await publicFetch("/auth/oauth/exchange", {
      method: "POST",
      body: JSON.stringify({
        provider: "azure",
        code,
        redirectUri: callbackUrl
      }),
    });

    if (!response.ok) {
      console.error("OAuth code exchange failed:", response.status);
      const loginUrl = new URL("/login", process.env.NEXTAUTH_URL);
      if (response.status === 403) {
        loginUrl.searchParams.set("error", "user_not_allowed");
      } else {
        loginUrl.searchParams.set("error", "Token exchange failed");
      }
      if (userCallbackUrl !== "/") {
        loginUrl.searchParams.set("callbackUrl", userCallbackUrl);
      }
      const redirectResponse = NextResponse.redirect(loginUrl);
      // Clear the cookie (must specify path to match the cookie that was set)
      redirectResponse.cookies.set("oauth-callback-url", "", { path: "/", maxAge: 0 });
      return redirectResponse;
    }

    const result = await response.json();

    const loginUrl = new URL("/login", process.env.NEXTAUTH_URL);
    loginUrl.searchParams.set("token", result.token);
    // Preserve the user's desired callback URL
    if (userCallbackUrl !== "/") {
      loginUrl.searchParams.set("callbackUrl", userCallbackUrl);
    }
    const redirectResponse = NextResponse.redirect(loginUrl);
    // Clear the cookie after successful use (must specify path to match the cookie that was set)
    redirectResponse.cookies.set("oauth-callback-url", "", { path: "/", maxAge: 0 });
    return redirectResponse;
  } catch (error) {
    console.error("Azure OAuth callback error:", error);
    const loginUrl = new URL("/login", process.env.NEXTAUTH_URL);
    loginUrl.searchParams.set("error", "Token exchange failed");
    if (userCallbackUrl !== "/") {
      loginUrl.searchParams.set("callbackUrl", userCallbackUrl);
    }
    const redirectResponse = NextResponse.redirect(loginUrl);
    // Clear the cookie (must specify path to match the cookie that was set)
    redirectResponse.cookies.set("oauth-callback-url", "", { path: "/", maxAge: 0 });
    return redirectResponse;
  }
}
