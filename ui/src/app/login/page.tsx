"use client";

import { useEffect, useRef, useState, Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { signIn } from "next-auth/react";
import { useAuth } from "@/hooks/useAuth";
import { sanitizeCallbackUrl } from "@/lib/utils/url";

function isInIframe(): boolean {
  try {
    return window.self !== window.top;
  } catch {
    return true;
  }
}

function LoginContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { isAuthenticated, isLoading } = useAuth();
  const authAttemptedRef = useRef(false);
  // Tracked as React state (not refs) so the bfcache-restore handler below can
  // re-render the page out of the "Redirecting to sign-in..." spinner.
  const [autoRedirectAttempted, setAutoRedirectAttempted] = useState(false);
  const [autoRedirecting, setAutoRedirecting] = useState(false);

  const code = searchParams.get("code");
  const callbackUrl = sanitizeCallbackUrl(searchParams.get("callbackUrl"));
  const error = searchParams.get("error");

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

  // bfcache: when the browser restores this page after the user pressed "back" from Dex,
  // clear the spinner so the SSO button renders as a fallback. autoRedirectAttempted stays
  // true so the auto-redirect effect doesn't re-fire and bounce the user straight back.
  useEffect(() => {
    const handlePageShow = (event: PageTransitionEvent) => {
      if (event.persisted) {
        setAutoRedirecting(false);
      }
    };
    window.addEventListener("pageshow", handlePageShow);
    return () => window.removeEventListener("pageshow", handlePageShow);
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

    if (code) {
      authAttemptedRef.current = true;
      performSignIn("backend-code", { code });
      return;
    }

    // If already authenticated, redirect
    if (isAuthenticated) {
      router.replace(callbackUrl);
    }
  }, [code, isAuthenticated, isLoading, callbackUrl, router]);

  // Auto-redirect to Dex on mount. Skipped on iframes (popups need a user gesture),
  // when an in-flight code is being completed, or when an error is being shown —
  // those paths need the page to render so the user sees the message or completes the flow.
  useEffect(() => {
    if (autoRedirectAttempted) return;
    if (isLoading) return;
    if (isAuthenticated) return;
    if (error || code) return;
    if (isInIframe()) return;

    const redirectTimer = window.setTimeout(() => {
      setAutoRedirectAttempted(true);
      setAutoRedirecting(true);
      window.location.href = `/api/oauth/start/dex?callbackUrl=${encodeURIComponent(callbackUrl)}`;
    }, 0);

    return () => window.clearTimeout(redirectTimer);
  }, [autoRedirectAttempted, isLoading, isAuthenticated, error, code, callbackUrl]);

  const handleLogin = () => {
    // OAuth goes through API route - server handles redirect to backend
    // Include callbackUrl so user returns to the right page after login
    const oauthUrl = `/api/oauth/start/dex?callbackUrl=${encodeURIComponent(callbackUrl)}`;

    if (!isInIframe()) {
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

  // Show loading state during auth checks, in-flight callbacks, or while the auto-redirect is firing.
  // The pre-emptive `willAutoRedirectSoon` clause covers the gap between first render and
  // the effect firing, so the SSO button doesn't flicker into view; once the effect has run,
  // `autoRedirectAttempted` flips and only the `autoRedirecting` state controls the spinner —
  // which the bfcache pageshow handler clears when the user pressed Back from Dex.
  const willAutoRedirectSoon =
    !autoRedirectAttempted && !isLoading && !isAuthenticated && !error && !code && !isInIframe();
  const showSpinner = isLoading || autoRedirecting || willAutoRedirectSoon || (code && !error);
  if (showSpinner) {
    const message =
      code
        ? "Completing authentication..."
        : autoRedirecting || willAutoRedirectSoon
          ? "Redirecting to sign-in..."
          : "Loading...";
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-900">
        <div className="text-center">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-gray-900 mx-auto mb-4"></div>
          <p className="text-gray-600">{message}</p>
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

  // Iframe path (popups need a user gesture) and bfcache fallback (when the user pressed
  // Back from Dex) both render the SSO button so the user can complete login manually.
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
          <button
            onClick={handleLogin}
            className="w-full flex items-center justify-center gap-3 px-4 py-3 border border-gray-300 rounded-lg shadow-sm bg-white hover:bg-gray-50 text-gray-700 font-medium transition-colors"
          >
            Continue with SSO
          </button>
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
