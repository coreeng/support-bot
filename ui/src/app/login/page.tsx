"use client";

import { useAuth } from "@/hooks/useAuth";
import { isOauthUiKnownProvider, type OauthUiKnownProvider } from "@/lib/auth/oauth-ui-callback";
import { sanitizeCallbackUrl } from "@/lib/utils/url";
import { signIn } from "next-auth/react";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useRef, useState } from "react";

type LoginProvider = OauthUiKnownProvider;

function LoginContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { isAuthenticated, isLoading } = useAuth();
  const authAttemptedRef = useRef(false);
  const [providers, setProviders] = useState<LoginProvider[]>(["google", "azure"]);
  const [providersLoading, setProvidersLoading] = useState(true);
  const [providersError, setProvidersError] = useState(false);

  const code = searchParams.get("code");
  const provider = searchParams.get("provider");
  const token = searchParams.get("token");
  const callbackUrl = sanitizeCallbackUrl(searchParams.get("callbackUrl"));
  const error = searchParams.get("error");

  /**
   * Fetch available providers from backend (skip if already authenticated).
   * Shows error message with no login options if backend is unreachable.
   */
  useEffect(() => {
    if (isAuthenticated) return;

    fetch("/api/identity-providers", { cache: "no-store" })
      .then((res) => res.json())
      .then((data) => {
        if (data.error) {
          console.error("[Login] Failed to fetch providers from backend");
          setProvidersError(true);
          setProviders([]);
        } else {
          const availableProviders = (data.providers || []).filter((p: string): p is LoginProvider => isOauthUiKnownProvider(p));
          setProviders(availableProviders);
          setProvidersError(false);
        }
      })
      .catch((error) => {
        console.error("[Login] Exception fetching providers:", error);
        setProvidersError(true);
        setProviders([]);
      })
      .finally(() => {
        setProvidersLoading(false);
      });
  }, [isAuthenticated]);

  const showProvidersLoading = !isAuthenticated && providersLoading;

  // Iframe: listen for auth completion from popup
  useEffect(() => {
    const handleMessage = (event: MessageEvent) => {
      if (event.origin !== window.location.origin) return;
      if (event.data?.type === "auth:success") {
        // Popup completed signIn — session cookie is already set (shared origin).
        // Sanitize the callbackUrl from the message to prevent open redirects.
        window.location.href = sanitizeCallbackUrl(event.data.callbackUrl);
      }
    };
    window.addEventListener("message", handleMessage);
    return () => window.removeEventListener("message", handleMessage);
  }, []);

  useEffect(() => {
    if (isLoading) return;

    // Don't retry if we already attempted authentication with this code
    if (authAttemptedRef.current) return;

    // Detect if we're in a popup opened by the iframe
    const isPopup = !!window.opener && !window.opener.closed;

    // Helper to perform sign-in with consistent error handling
    const performSignIn = (providerId: string, credentials: Record<string, string>) => {
      signIn(providerId, { ...credentials, redirect: false })
        .then((result) => {
          if (result?.error) {
            router.replace(`/login?error=${encodeURIComponent(result.error)}`);
            return;
          }
          // Success: different action for popup vs non-popup
          if (isPopup) {
            window.opener!.postMessage({ type: "auth:success", callbackUrl }, window.location.origin);
            window.close();
          } else {
            window.location.href = callbackUrl;
          }
        })
        .catch((error) => {
          router.replace(`/login?error=${encodeURIComponent(error)}`);
        });
    };

    if (token) {
      authAttemptedRef.current = true;
      performSignIn("backend-token", { token });
      return;
    }

    if (code && provider) {
      authAttemptedRef.current = true;
      performSignIn("backend-code", { code, provider });
      return;
    }

    // If already authenticated, redirect
    if (isAuthenticated) {
      router.replace(callbackUrl);
    }
  }, [code, provider, token, isAuthenticated, isLoading, callbackUrl, router]);

  const handleLogin = (provider: LoginProvider) => {
    // OAuth goes through API route - server handles redirect to backend
    // Include callbackUrl so user returns to the right page after login
    const oauthUrl = `/api/oauth/start/${provider}?callbackUrl=${encodeURIComponent(callbackUrl)}`;

    // Check if we're in an iframe
    const isInIframe = (() => {
      try {
        return window.self !== window.top;
      } catch {
        return true;
      }
    })();

    if (!isInIframe) {
      window.location.href = oauthUrl;
      return;
    }

    // Popup mode for iframes
    const width = 600;
    const height = 720;
    const left = window.screenX + (window.outerWidth - width) / 2;
    const top = window.screenY + (window.outerHeight - height) / 2;
    const popup = window.open(oauthUrl, "supportbot-auth", `popup=yes,width=${width},height=${height},left=${left},top=${top}`);

    if (!popup) {
      window.location.href = oauthUrl;
    } else {
      popup.focus();
    }
  };

  // Show loading state (but not if auth already failed - let error screen show)
  if (isLoading || showProvidersLoading || ((code || token) && !error)) {
    return (
      <div className="bg-background flex min-h-screen items-center justify-center">
        <div className="text-center">
          <div className="border-foreground mx-auto mb-4 h-8 w-8 animate-spin rounded-full border-b-2"></div>
          <p className="text-muted-foreground">{code || token ? "Completing authentication..." : "Loading..."}</p>
        </div>
      </div>
    );
  }

  if (error === "configuration") {
    return (
      <div className="bg-background flex min-h-screen items-center justify-center">
        <div className="w-full max-w-md space-y-8 p-8 text-center">
          <h2 className="text-foreground text-2xl font-bold">Sign-in unavailable</h2>
          <p className="text-muted-foreground">
            This deployment is misconfigured: the public app URL (<code className="text-sm">NEXTAUTH_URL</code>) must be set and must match
            the API&apos;s expected UI origin (<code className="text-sm">UI_ORIGIN</code>). Contact your administrator.
          </p>
          <button type="button" onClick={() => router.replace("/login")} className="text-primary hover:underline">
            Back to login
          </button>
        </div>
      </div>
    );
  }

  // Show not-onboarded message for allow-list rejections
  if (error === "user_not_allowed") {
    return (
      <div className="bg-background flex min-h-screen items-center justify-center">
        <div className="w-full max-w-md space-y-8 p-8 text-center">
          <h2 className="text-foreground text-2xl font-bold">Access Restricted</h2>
          <p className="text-muted-foreground">
            You have successfully authenticated but your user has not been onboarded to the Support UI.
          </p>
          <p className="text-muted-foreground text-sm">Please contact your administrator for access.</p>
          <button onClick={() => router.replace("/login")} className="text-primary hover:underline">
            Back to login
          </button>
        </div>
      </div>
    );
  }

  // Show error if present
  if (error) {
    return (
      <div className="bg-background flex min-h-screen items-center justify-center">
        <div className="w-full max-w-md space-y-8 p-8 text-center">
          <h2 className="text-destructive text-2xl font-bold">Authentication Error</h2>
          <p className="text-muted-foreground">{error}</p>
          <button onClick={() => router.replace("/login")} className="text-primary hover:underline">
            Try again
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="bg-background flex min-h-screen items-center justify-center">
      <div className="w-full max-w-md space-y-8 p-8">
        <div className="text-center">
          <h2 className="text-foreground text-3xl font-bold">Sign in</h2>
          <p className="text-muted-foreground mt-2">Choose your authentication method</p>
        </div>

        <div className="space-y-4">
          {providersError && (
            <div className="text-muted-foreground bg-destructive/10 rounded-lg p-4 text-center text-sm">
              Unable to fetch identity provider configuration from backend.
            </div>
          )}

          {!providersError && providers.length === 0 && (
            <div className="text-muted-foreground p-4 text-center text-sm">No authentication providers configured.</div>
          )}

          {providers.includes("google") && (
            <button
              onClick={() => handleLogin("google")}
              className="border-border bg-card hover:bg-accent text-foreground flex w-full items-center justify-center gap-3 rounded-lg border px-4 py-3 font-medium shadow-sm transition-colors"
            >
              <svg className="h-5 w-5" viewBox="0 0 24 24">
                <path
                  fill="#4285F4"
                  d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
                />
                <path
                  fill="#34A853"
                  d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
                />
                <path
                  fill="#FBBC05"
                  d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"
                />
                <path
                  fill="#EA4335"
                  d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"
                />
              </svg>
              Continue with Google
            </button>
          )}

          {providers.includes("azure") && (
            <button
              onClick={() => handleLogin("azure")}
              className="border-border bg-card hover:bg-accent text-foreground flex w-full items-center justify-center gap-3 rounded-lg border px-4 py-3 font-medium shadow-sm transition-colors"
            >
              <svg className="h-5 w-5" viewBox="0 0 23 23">
                <path fill="#f35325" d="M1 1h10v10H1z" />
                <path fill="#81bc06" d="M12 1h10v10H12z" />
                <path fill="#05a6f0" d="M1 12h10v10H1z" />
                <path fill="#ffba08" d="M12 12h10v10H12z" />
              </svg>
              Continue with Microsoft
            </button>
          )}

          {providers.includes("dex") && (
            <button
              onClick={() => handleLogin("dex")}
              className="border-border bg-card hover:bg-accent text-foreground flex w-full items-center justify-center gap-3 rounded-lg border px-4 py-3 font-medium shadow-sm transition-colors"
            >
              Continue with SSO
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

export default function LoginPage() {
  return (
    <Suspense
      fallback={
        <div className="bg-background flex min-h-screen items-center justify-center">
          <div className="border-foreground h-8 w-8 animate-spin rounded-full border-b-2"></div>
        </div>
      }
    >
      <LoginContent />
    </Suspense>
  );
}
