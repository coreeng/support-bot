import {NextRequest, NextResponse} from "next/server";
import {publicFetch} from "../../../_lib/public-fetch";
import {sanitizeCallbackUrl} from "@/lib/utils/url";

export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ provider: string }> }
) {
  const { provider } = await params;

  if (provider === "google" || provider === "azure" || provider === "dex") {
    const redirectUri = new URL(
      `/api/oauth/callback/${provider}`,
      process.env.NEXTAUTH_URL
    ).toString();

    const urlParams = new URLSearchParams({provider, redirectUri});
    const oauthUrl = await publicFetch(`/auth/oauth-url?${urlParams.toString()}`);

    if (oauthUrl.ok) {
      try {
        const {url} = await oauthUrl.json();
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
          console.error("Backend returned empty OAuth URL");
          return NextResponse.json({error: "Backend returned empty OAuth URL"}, {status: 500});
        }
      } catch (error) {
        console.error("OAuth start error: ", error);
        return NextResponse.json({error: "Failed to start OAuth flow"}, {status: 500});
      }
    } else {
      console.error(`Failed to get OAuth URL: ${oauthUrl.status}`);
      return NextResponse.json({ error: "Failed to get OAuth URL" }, { status: oauthUrl.status });
    }
  } else {
    console.error(`Invalid provider: "${provider}"`);
    return NextResponse.json({error: `Invalid provider: "${provider}"` }, { status: 400 });
  }
}
