import {NextRequest, NextResponse} from "next/server";
import {sanitizeCallbackUrl} from "@/lib/utils/url";

export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ provider: string }> }
) {
  const {provider} = await params;
  const searchParams = request.nextUrl.searchParams;
  const code = searchParams.get("code");
  const error = searchParams.get("error");


  const loginUrl = new URL("/login", process.env.NEXTAUTH_URL);
  if (error) {
    const KNOWN_ERRORS = ["access_denied", "user_not_allowed", "server_error", "temporarily_unavailable"];
    const safeError = KNOWN_ERRORS.includes(error) ? error : "authentication_failed";
    loginUrl.searchParams.set("error", safeError);
  } else if (provider !== "google" && provider !== "azure") {
    loginUrl.searchParams.set("error", "Invalid OAuth provider");
  } else if (code) {
    loginUrl.searchParams.set("code", code);
  } else {
    loginUrl.searchParams.set("error", "No authorization code received");
  }
  loginUrl.searchParams.set("provider", provider);
  // Extract from cookie the user's last visited page to redirect to after login
  const rawCallbackUrl = request.cookies.get("oauth-callback-url")?.value || "/";
  loginUrl.searchParams.set("callbackUrl", sanitizeCallbackUrl(rawCallbackUrl));
  const redirectResponse = NextResponse.redirect(loginUrl);
  // Clear the cookie after successful use (must specify path to match the cookie that was set)
  redirectResponse.cookies.set("oauth-callback-url", "", { path: "/", maxAge: 0 });
  return redirectResponse;
}

