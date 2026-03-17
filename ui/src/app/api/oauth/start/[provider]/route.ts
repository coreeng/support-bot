import {NextRequest, NextResponse} from "next/server";
import {publicFetch} from "../../../_lib/public-fetch";

/**
 * Sanitize a callback URL to prevent open-redirect attacks.
 * Only relative paths (starting with "/" but not "//") are allowed.
 * Anything else (absolute URLs, protocol-relative, javascript: etc.) falls back to "/".
 */
function sanitizeCallbackUrl(url: string | null | undefined): string {
  if (typeof url === "string" && url.startsWith("/") && !url.startsWith("//")) {
    return url;
  }
  return "/";
}

export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ provider: string }> }
) {
  const { provider } = await params;

  if (provider === "google" || provider === "azure") {
    const redirectUri = new URL(
      `/api/oauth/callback/${provider}`,
      process.env.NEXTAUTH_URL
    ).toString();

    const urlParams = new URLSearchParams({provider, redirectUri});
    const oauthUrl = await publicFetch(`/auth/oauth-url?${urlParams.toString()}`);

    try {
      const { url } = await oauthUrl.json();
      if (url) {
        const providerRedirect = NextResponse.redirect(url);

        // Remember the page the user was on before redirecting to the login page
        const rawCallbackUrl = request.nextUrl.searchParams.get("callbackUrl");
        const userCallbackUrl = sanitizeCallbackUrl(rawCallbackUrl);

        providerRedirect.cookies.set("oauth-callback-url", userCallbackUrl, {
          httpOnly: true,
          secure: process.env.NODE_ENV === "production",
          sameSite: "lax",
          maxAge: 600, // 10 minutes - enough time to complete OAuth
          path: "/",
        });

        return providerRedirect;
      } else {
        return NextResponse.json({ error: "Backend returned empty OAuth URL" }, { status: 500 });
      }
    } catch (error) {
      console.error("OAuth start error:", error);
      return NextResponse.json({ error: "Failed to start OAuth flow" }, { status: 500 });
    }
  } else {
    console.error(`Invalid provider: "${provider}"`);
    return NextResponse.json({error: `Invalid provider: "${provider}"` }, { status: 400 });
  }
}
