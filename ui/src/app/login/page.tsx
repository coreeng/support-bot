"use client";

import { useEffect, useRef, useState, Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { signIn } from "next-auth/react";
import { useAuth } from "@/hooks/useAuth";
import {
  isOauthUiKnownProvider,
  type OauthUiKnownProvider,
} from "@/lib/auth/oauth-ui-callback";
import { sanitizeCallbackUrl } from "@/lib/utils/url";

type LoginProvider = OauthUiKnownProvider;

function LoginContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { isAuthenticated, isLoading } = useAuth();
  const authAttemptedRef = useRef(false);
  const autoRedirectAttemptedRef = useRef(false);
  const [providers, setProviders] = useState<LoginProvider[]>([]);
  const [providersLoading, setProvidersLoading] = useState(true);
  const [providersError, setProvidersError] = useState(false);
  const [autoRedirecting, setAutoRedirecting] = useState(false);

  const code = searchParams.get("code");
  const provider = searchParams.get("provider");
  const token = searchParams.get("token");
  const callbackUrl = sanitizeCallbackUrl(searchParams.get("callbackUrl"));
  const error = searchParams.get("error");
  const signOut = searchParams.get("signOut");

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
          const availableProviders = (data.providers || []).filter((p: string): p is LoginProvider =>
            isOauthUiKnownProvider(p)
          );
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

    // Don't retry if we already attempted authentication with this token/code
    if (authAttemptedRef.current) return;

    // Detect if we're in a popup opened by the iframe
    const isPopup = !!window.opener && !window.opener.closed;

    // If we have a token from the new OAuth flow, use it
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
            window.opener!.postMessage(
              { type: "auth:success", callbackUrl },
              window.location.origin
            );
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

  // Auto-redirect to Dex when it's the only configured provider. Skipped on iframes
  // (popups need a user gesture), after explicit sign-out, when an in-flight code/token
  // is being completed, or when an error is being shown — those paths need the page to
  // render so the user sees the message or completes the flow.
  useEffect(() => {
    if (autoRedirectAttemptedRef.current) return;
    if (isLoading || providersLoading) return;
    if (isAuthenticated) return;
    if (error || code || token) return;
    if (signOut) return;
    if (providersError) return;
    if (providers.length !== 1 || providers[0] !== "dex") return;

    const isInIframe = (() => {
      try {
        return window.self !== window.top;
      } catch {
        return true;
      }
    })();
    if (isInIframe) return;

    autoRedirectAttemptedRef.current = true;
    setAutoRedirecting(true);
    window.location.href = `/api/oauth/start/dex?callbackUrl=${encodeURIComponent(callbackUrl)}`;
  }, [
    isLoading,
    providersLoading,
    isAuthenticated,
    providers,
    providersError,
    error,
    code,
    token,
    signOut,
    callbackUrl,
  ]);

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
    const popup = window.open(
      oauthUrl,
      "supportbot-auth",
      `popup=yes,width=${width},height=${height},left=${left},top=${top}`
    );

    if (!popup) {
      window.location.href = oauthUrl;
    } else {
      popup.focus();
    }
  };

  // Show loading state (but not if auth already failed - let error screen show)
  if (isLoading || showProvidersLoading || autoRedirecting || ((code || token) && !error)) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-900">
        <div className="text-center">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-gray-900 mx-auto mb-4"></div>
          <p className="text-gray-600">
            {code || token
              ? "Completing authentication..."
              : autoRedirecting
                ? "Redirecting to sign-in..."
                : "Loading..."}
          </p>
        </div>
      </div>
    );
  }

  if (error === "configuration") {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-900">
        <div className="max-w-md w-full space-y-8 p-8 text-center">
          <h2 className="text-2xl font-bold text-gray-900 dark:text-white">Sign-in unavailable</h2>
          <p className="text-gray-600 dark:text-gray-400">
            This deployment is misconfigured: the public app URL (<code className="text-sm">NEXTAUTH_URL</code>)
            must be set and must match the API&apos;s expected UI origin (<code className="text-sm">UI_ORIGIN</code>).
            Contact your administrator.
          </p>
          <button
            type="button"
            onClick={() => router.replace("/login")}
            className="text-blue-600 hover:underline"
          >
            Back to login
          </button>
        </div>
      </div>
    );
  }

  // Show not-onboarded message for allow-list rejections
  if (error === "user_not_allowed") {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-900">
        <div className="max-w-md w-full space-y-8 p-8 text-center">
          <h2 className="text-2xl font-bold text-gray-900 dark:text-white">Access Restricted</h2>
          <p className="text-gray-600 dark:text-gray-400">
            You have successfully authenticated but your user has not been
            onboarded to the Support UI.
          </p>
          <p className="text-gray-500 dark:text-gray-500 text-sm">
            Please contact your administrator for access.
          </p>
          <button
            onClick={() => router.replace("/login")}
            className="text-blue-600 hover:underline"
          >
            Back to login
          </button>
        </div>
      </div>
    );
  }

  // Show error if present
  if (error) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-900">
        <div className="max-w-md w-full space-y-8 p-8 text-center">
          <h2 className="text-2xl font-bold text-red-600">Authentication Error</h2>
          <p className="text-gray-600">{error}</p>
          <button
            onClick={() => router.replace("/login")}
            className="text-blue-600 hover:underline"
          >
            Try again
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-900">
      <div className="max-w-md w-full space-y-8 p-8">
        <div className="text-center">
          <h2 className="text-3xl font-bold text-gray-900 dark:text-white">Sign in</h2>
          <p className="mt-2 text-gray-600 dark:text-gray-400">
            Choose your authentication method
          </p>
        </div>

        <div className="space-y-4">
          {providersError && (
            <div className="text-sm text-amber-700 text-center p-4 bg-amber-50 rounded-lg dark:bg-amber-900/20 dark:text-amber-400">
              Unable to fetch identity provider configuration from backend.
            </div>
          )}

          {!providersError && providers.length === 0 && (
            <div className="text-sm text-gray-600 text-center p-4">
              No authentication providers configured.
            </div>
          )}

          {providers.includes("dex") && (
            <button
              onClick={() => handleLogin("dex")}
              className="w-full flex items-center justify-center gap-3 px-4 py-3 border border-gray-300 rounded-lg shadow-sm bg-white hover:bg-gray-50 text-gray-700 font-medium transition-colors"
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
        <div className="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-900">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-gray-900"></div>
        </div>
      }
    >
      <LoginContent />
    </Suspense>
  );
}
