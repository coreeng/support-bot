import {NextRequest, NextResponse} from "next/server";
import {tryResolvePublicOrigin} from "@/lib/server/resolve-public-origin-response";
import {sanitizeCallbackUrl} from "@/lib/utils/url";

export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ provider: string }> }
) {
  const {provider} = await params;
  const searchParams = request.nextUrl.searchParams;
  const code = searchParams.get("code");
  const error = searchParams.get("error");
  const returnedState = searchParams.get("state");

  const resolved = tryResolvePublicOrigin(
    request.nextUrl.origin,
    sanitizeCallbackUrl(request.cookies.get("oauth-callback-url")?.value)
  );
  if (!resolved.ok) {
    return resolved.response;
  }
  const loginUrl = new URL("/login", resolved.origin);
  // Extract from cookie the user's last visited page to redirect to after login
  const rawCallbackUrl = request.cookies.get("oauth-callback-url")?.value || "/";
  loginUrl.searchParams.set("callbackUrl", sanitizeCallbackUrl(rawCallbackUrl));

  const stateCookie = request.cookies.get("oauth-state")?.value;
  const expectedState = stateCookie?.startsWith(`${provider}:`)
    ? stateCookie.slice(provider.length + 1)
    : undefined;
  if (!expectedState || !returnedState || expectedState !== returnedState) {
    console.error("OAuth state mismatch — possible CSRF or provider confusion");
    loginUrl.searchParams.set("error", "authentication_failed");
    const redirectResponse = NextResponse.redirect(loginUrl);
    redirectResponse.cookies.set("oauth-callback-url", "", { path: "/", maxAge: 0 });
    redirectResponse.cookies.set("oauth-state", "", { path: "/", maxAge: 0 });
    return redirectResponse;
  }

  // Add provider and code/error parameters BEFORE creating the redirect response
  if (provider === "dex") {
    loginUrl.searchParams.set("provider", provider);
    if (code) {
      loginUrl.searchParams.set("code", code);
    } else if (error) {
      const KNOWN_ERRORS = ["access_denied", "user_not_allowed", "server_error", "temporarily_unavailable"];
      const safeError = KNOWN_ERRORS.includes(error) ? error : "authentication_failed";
      loginUrl.searchParams.set("error", safeError);
    } else {
      loginUrl.searchParams.set("error", "No authorization code received");
    }
  }

  // Create redirect response with the complete URL including all parameters
  const redirectResponse = NextResponse.redirect(loginUrl);
  // Clear cookies after use (must specify path to match the cookie that was set)
  redirectResponse.cookies.set("oauth-callback-url", "", { path: "/", maxAge: 0 });
  redirectResponse.cookies.set("oauth-state", "", { path: "/", maxAge: 0 });

  return redirectResponse;
}

