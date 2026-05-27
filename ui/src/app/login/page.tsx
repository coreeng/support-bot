"use client";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { useAuth } from "@/hooks/useAuth";
import { isOauthUiKnownProvider, type OauthUiKnownProvider } from "@/lib/auth/oauth-ui-callback";
import { sanitizeCallbackUrl } from "@/lib/utils/url";
import { Loader2, ShieldCheck } from "lucide-react";
import { signIn } from "next-auth/react";
import Image from "next/image";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useRef, useState } from "react";

type LoginProvider = OauthUiKnownProvider;

function LoginShell({ children }: { children: React.ReactNode }) {
  return (
    <div
      className="flex min-h-screen items-center justify-center bg-cover bg-fixed bg-center p-6"
      style={{
        backgroundImage: "linear-gradient(rgb(0 0 0 / 0.35), rgb(0 0 0 / 0.35)), url(/bg.png)",
      }}
    >
      <Card className="w-full max-w-[28rem] gap-0 py-0">
        <CardHeader className="justify-items-center pt-7 pb-0">
          <Image src="/logo.png" alt="Support Bot" width={200} height={50} priority className="col-span-full dark:hidden" />
          <Image src="/logo-dark.png" alt="Support Bot" width={200} height={50} priority className="col-span-full hidden dark:block" />
        </CardHeader>
        <CardContent className="px-7 pt-6 pb-7">{children}</CardContent>
      </Card>
    </div>
  );
}

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

  useEffect(() => {
    const handleMessage = (event: MessageEvent) => {
      if (event.origin !== window.location.origin) return;
      if (event.data?.type === "auth:success") {
        window.location.href = sanitizeCallbackUrl(event.data.callbackUrl);
      }
    };
    window.addEventListener("message", handleMessage);
    return () => window.removeEventListener("message", handleMessage);
  }, []);

  useEffect(() => {
    if (isLoading) return;
    if (authAttemptedRef.current) return;

    const isPopup = !!window.opener && !window.opener.closed;

    const performSignIn = (providerId: string, credentials: Record<string, string>) => {
      signIn(providerId, { ...credentials, redirect: false })
        .then((result) => {
          if (result?.error) {
            router.replace(`/login?error=${encodeURIComponent(result.error)}`);
            return;
          }
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

    if (isAuthenticated) {
      router.replace(callbackUrl);
    }
  }, [code, provider, token, isAuthenticated, isLoading, callbackUrl, router]);

  const handleLogin = (loginProvider: LoginProvider) => {
    const oauthUrl = `/api/oauth/start/${loginProvider}?callbackUrl=${encodeURIComponent(callbackUrl)}`;

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

  if (isLoading || showProvidersLoading || ((code || token) && !error)) {
    return (
      <LoginShell>
        <div className="flex flex-col items-center gap-3">
          <Loader2 className="text-muted-foreground h-6 w-6 animate-spin" />
          <p className="text-muted-foreground text-sm">{code || token ? "Completing authentication..." : "Loading..."}</p>
        </div>
      </LoginShell>
    );
  }

  if (error === "configuration") {
    return (
      <LoginShell>
        <h1 className="text-foreground text-xl font-bold tracking-tight">Sign-in unavailable</h1>
        <p className="text-muted-foreground mt-1 text-sm">
          This deployment is misconfigured: <code className="bg-muted rounded px-1 py-0.5 font-mono text-xs">NEXTAUTH_URL</code> must be set
          and must match <code className="bg-muted rounded px-1 py-0.5 font-mono text-xs">UI_ORIGIN</code>. Contact your administrator.
        </p>
        <div className="mt-4 text-center">
          <Button variant="link" onClick={() => router.replace("/login")} className="text-muted-foreground text-xs">
            Back to login
          </Button>
        </div>
      </LoginShell>
    );
  }

  if (error === "user_not_allowed") {
    return (
      <LoginShell>
        <h1 className="text-foreground text-xl font-bold tracking-tight">Access Restricted</h1>
        <p className="text-muted-foreground mt-1 text-sm">
          You have successfully authenticated but your user has not been onboarded to the Support UI.
        </p>
        <p className="text-muted-foreground mt-1 text-xs">Please contact your administrator for access.</p>
        <div className="mt-4 text-center">
          <Button variant="link" onClick={() => router.replace("/login")} className="text-muted-foreground text-xs">
            Back to login
          </Button>
        </div>
      </LoginShell>
    );
  }

  if (error) {
    return (
      <LoginShell>
        <h1 className="text-foreground text-xl font-bold tracking-tight">Authentication Error</h1>
        <div className="bg-destructive/10 text-destructive border-destructive/30 mt-3 rounded-md border px-3 py-2.5 text-sm">{error}</div>
        <div className="mt-4 text-center">
          <Button variant="link" onClick={() => router.replace("/login")} className="text-muted-foreground text-xs">
            Try again
          </Button>
        </div>
      </LoginShell>
    );
  }

  return (
    <LoginShell>
      <div className="mb-4">
        <h1 className="text-foreground text-xl font-bold tracking-tight">Log in to Support Bot</h1>
        <p className="text-muted-foreground mt-1 text-sm">Choose a sign-in method to continue.</p>
        <Separator className="mt-3" />
      </div>

      {providersError && (
        <div className="bg-destructive/10 text-destructive border-destructive/30 mb-3 rounded-md border px-3 py-2.5 text-center text-sm">
          Unable to fetch identity provider configuration from backend.
        </div>
      )}

      {!providersError && providers.length === 0 && (
        <p className="text-muted-foreground py-2 text-center text-sm">No authentication providers configured.</p>
      )}

      <div className="flex flex-col gap-2.5">
        {providers.includes("google") && (
          <Button variant="outline" size="lg" className="w-full gap-2.5" onClick={() => handleLogin("google")}>
            <Image src="/google.svg" alt="" width={20} height={20} className="size-5" />
            Sign in with Google
          </Button>
        )}
        {providers.includes("azure") && (
          <Button variant="outline" size="lg" className="w-full gap-2.5" onClick={() => handleLogin("azure")}>
            <Image src="/microsoft.svg" alt="" width={20} height={20} className="size-5" />
            Sign in with Microsoft
          </Button>
        )}
        {providers.includes("dex") && (
          <Button variant="outline" size="lg" className="w-full gap-2.5" onClick={() => handleLogin("dex")}>
            <ShieldCheck className="size-5" />
            Sign in with SSO
          </Button>
        )}
      </div>
    </LoginShell>
  );
}

export default function LoginPage() {
  return (
    <Suspense
      fallback={
        <LoginShell>
          <div className="flex justify-center">
            <Loader2 className="text-muted-foreground h-6 w-6 animate-spin" />
          </div>
        </LoginShell>
      }
    >
      <LoginContent />
    </Suspense>
  );
}
