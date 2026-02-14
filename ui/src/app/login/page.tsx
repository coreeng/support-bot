"use client";

import { useEffect, useRef, Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { signIn } from "next-auth/react";
import { useAuth } from "@/hooks/useAuth";

/**
 * Sanitize a callback URL to prevent open-redirect attacks.
 * Only relative paths (starting with "/" but not "//") are allowed.
 * Anything else (absolute URLs, protocol-relative, javascript: etc.) falls back to "/".
 */
export function sanitizeCallbackUrl(url: string | null | undefined): string {
  if (typeof url === "string" && url.startsWith("/") && !url.startsWith("//")) {
    return url;
  }
  return "/";
}

function LoginContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { isAuthenticated, isLoading } = useAuth();
  const authAttemptedRef = useRef(false);

  // Handle callback from backend OAuth
  const code = searchParams.get("code");
  const token = searchParams.get("token");
  const callbackUrl = sanitizeCallbackUrl(searchParams.get("callbackUrl"));
  const error = searchParams.get("error");

  // Iframe: listen for auth completion from popup
  useEffect(() => {
    const handleMessage = (event: MessageEvent) => {
      if (event.origin !== window.location.origin) return;
      if (event.data?.type === "auth:success") {
        // Popup completed signIn — session cookie is already set (shared origin).
        // Sanitize the callbackUrl from the message to prevent open redirects.
        const targetUrl = sanitizeCallbackUrl(event.data.callbackUrl);
        window.location.href = targetUrl;
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
    if (token) {
      authAttemptedRef.current = true;

      if (isPopup) {
        // Popup: complete signIn here (first-party context, no CSRF issues),
        // then notify the iframe and close.
        signIn("backend-token", { token, redirect: false }).then((result) => {
          if (result?.error) {
            router.replace(`/login?error=${encodeURIComponent(result.error)}`);
            return;
          }
          window.opener!.postMessage(
            { type: "auth:success", callbackUrl },
            window.location.origin
          );
          window.close();
        });
        return;
      }

      signIn("backend-token", {
        token,
        callbackUrl,
        redirect: true,
      });
      return;
    }

    // If we have a code, exchange it via NextAuth (legacy flow)
    if (code) {
      authAttemptedRef.current = true;

      if (isPopup) {
        signIn("backend-oauth", { code, redirect: false }).then((result) => {
          if (result?.error) {
            router.replace(`/login?error=${encodeURIComponent(result.error)}`);
            return;
          }
          window.opener!.postMessage(
            { type: "auth:success", callbackUrl },
            window.location.origin
          );
          window.close();
        });
        return;
      }

      signIn("backend-oauth", {
        code,
        callbackUrl,
        redirect: true,
      });
      return;
    }

    // If already authenticated, redirect
    if (isAuthenticated) {
      router.replace(callbackUrl);
    }
  }, [code, token, isAuthenticated, isLoading, callbackUrl, router]);

  const handleLogin = (provider: "google" | "azure") => {
    // OAuth goes through API route - server handles redirect to backend
    const oauthUrl = `/api/auth/start/${provider}`;

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
  if (isLoading || ((code || token) && !error)) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-900">
        <div className="text-center">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-gray-900 mx-auto mb-4"></div>
          <p className="text-gray-600">
            {code || token ? "Completing authentication..." : "Loading..."}
          </p>
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
          <button
            onClick={() => handleLogin("google")}
            className="w-full flex items-center justify-center gap-3 px-4 py-3 border border-gray-300 rounded-lg shadow-sm bg-white hover:bg-gray-50 text-gray-700 font-medium transition-colors"
          >
            <svg className="w-5 h-5" viewBox="0 0 24 24">
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

          <button
            onClick={() => handleLogin("azure")}
            className="w-full flex items-center justify-center gap-3 px-4 py-3 border border-gray-300 rounded-lg shadow-sm bg-white hover:bg-gray-50 text-gray-700 font-medium transition-colors"
          >
            <svg className="w-5 h-5" viewBox="0 0 23 23">
              <path fill="#f35325" d="M1 1h10v10H1z" />
              <path fill="#81bc06" d="M12 1h10v10H12z" />
              <path fill="#05a6f0" d="M1 12h10v10H1z" />
              <path fill="#ffba08" d="M12 12h10v10H12z" />
            </svg>
            Continue with Microsoft
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
