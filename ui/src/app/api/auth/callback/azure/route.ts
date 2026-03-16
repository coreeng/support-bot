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

    const loginUrl = new URL("/login", process.env.NEXTAUTH_URL);
    loginUrl.searchParams.set("code", code);
    loginUrl.searchParams.set("provider", "azure");
    // Preserve the user's desired callback URL
    if (userCallbackUrl !== "/") {
      loginUrl.searchParams.set("callbackUrl", userCallbackUrl);
    }
    const redirectResponse = NextResponse.redirect(loginUrl);
    // Clear the cookie after successful use (must specify path to match the cookie that was set)
    redirectResponse.cookies.set("oauth-callback-url", "", { path: "/", maxAge: 0 });
    return redirectResponse;
}
