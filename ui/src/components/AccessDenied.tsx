"use client";

import { useAuth } from "@/hooks/useAuth";
import { LogOut, ShieldX } from "lucide-react";

export function AccessDenied() {
  const { user, logout, isLoading } = useAuth();

  if (isLoading) return null;

  return (
    <div className="bg-background flex h-full min-h-[400px] items-center justify-center">
      <div className="mx-4 w-full max-w-md text-center">
        <div className="bg-card space-y-6 rounded-2xl p-8 shadow-lg">
          <div className="bg-warning/10 inline-flex h-16 w-16 items-center justify-center rounded-full">
            <ShieldX className="text-warning h-8 w-8" />
          </div>
          <div>
            <h1 className="text-foreground text-2xl font-semibold">Access Restricted</h1>
            <p className="text-muted-foreground mt-2">
              Your account does not have the required role to view this page. A <span className="font-medium">Support Engineer</span> or{" "}
              <span className="font-medium">Leadership</span> role is required.
            </p>
          </div>
          {user?.email && (
            <p className="text-muted-foreground text-sm">
              Signed in as <span className="text-foreground font-medium">{user.email}</span>
            </p>
          )}
          <div className="space-y-3 pt-2">
            <p className="text-muted-foreground text-sm">Contact your administrator to request the appropriate role.</p>
            <button
              onClick={logout}
              className="bg-muted hover:bg-accent text-foreground inline-flex items-center gap-2 rounded-lg px-4 py-2 text-sm font-medium transition-colors"
            >
              <LogOut className="h-4 w-4" />
              Sign in with a different account
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

export function RequireDashboardAccess({ children }: { children: React.ReactNode }) {
  const { isLeadership, isSupportEngineer, isLoading } = useAuth();

  if (isLoading) {
    return (
      <div className="bg-background flex h-screen items-center justify-center">
        <div className="text-center">
          <div className="border-primary mb-4 inline-block h-12 w-12 animate-spin rounded-full border-b-2" />
          <p className="text-muted-foreground">Loading...</p>
        </div>
      </div>
    );
  }

  if (!isLeadership && !isSupportEngineer) {
    return <AccessDenied />;
  }

  return <>{children}</>;
}
