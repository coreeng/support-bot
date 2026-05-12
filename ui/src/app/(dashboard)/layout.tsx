"use client";

import { DashboardLayout } from "@/components/layout/DashboardLayout";
import { Suspense } from "react";

/**
 * Shared layout for all dashboard pages.
 * This layout persists across navigation within the (dashboard) route group,
 * preserving sidebar state (collapsed/expanded) and other layout-level state.
 *
 * The <Suspense> boundary is required by Next.js App Router so that any page
 * component (or hook) that calls `useSearchParams()` — including `useUrlParams`
 * — can do so without opting the entire route out of static rendering.
 */
export default function Layout({ children }: { children: React.ReactNode }) {
  return (
    <Suspense fallback={<div className="flex h-screen items-center justify-center">Loading…</div>}>
      <DashboardLayout>{children}</DashboardLayout>
    </Suspense>
  );
}
