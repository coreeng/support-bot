import {NextRequest, NextResponse} from "next/server";
import {publicFetch} from "../../../_lib/public-fetch";
import {tryResolvePublicOrigin} from "@/lib/server/resolve-public-origin";
import {sanitizeCallbackUrl} from "@/lib/utils/url";

export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ provider: string }> }
) {
  const { provider } = await params;

  if (provider === "google" || provider === "azure" || provider === "dex") {
    const resolved = tryResolvePublicOrigin();
    if (!resolved.ok) {
      return resolved.response;
    }
    const { origin } = resolved;
    const redirectUri = new URL(
      `/api/oauth/callback/${provider}`,
      origin
    ).toString();

    const urlParams = new URLSearchParams({provider, redirectUri});
    const oauthUrl = await publicFetch(`/auth/oauth-url?${urlParams.toString()}`);

    if (oauthUrl.ok) {
      try {
        const {url, state} = await oauthUrl.json();
        if (!url) {
          console.error("Backend returned empty OAuth URL");
          return NextResponse.json({error: "Backend returned empty OAuth URL"}, {status: 500});
        }
        if (!state) {
          console.error("Backend did not return OAuth state");
          return NextResponse.json({error: "Backend did not return OAuth state"}, {status: 500});
        }

        const providerRedirect = NextResponse.redirect(url);

        const rawCallbackUrl = request.nextUrl.searchParams.get("callbackUrl");
        const userCallbackUrl = sanitizeCallbackUrl(rawCallbackUrl);

        providerRedirect.cookies.set("oauth-callback-url", userCallbackUrl, {
          httpOnly: true,
          secure: process.env.NODE_ENV === "production",
          sameSite: "lax",
          maxAge: 600,
          path: "/",
        });

        providerRedirect.cookies.set("oauth-state", `${provider}:${state}`, {
          httpOnly: true,
          secure: process.env.NODE_ENV === "production",
          sameSite: "lax",
          maxAge: 600,
          path: "/",
        });

        return providerRedirect;
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
