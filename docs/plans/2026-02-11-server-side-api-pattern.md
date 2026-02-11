# Server-Side API Pattern Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Align UI codebase with server-side API proxy pattern - all backend communication through server-only API layer.

**Architecture:** Hybrid approach - API routes for queries (preserves React Query), Server Actions for mutations. Remove middleware proxy. Create `lib/api/*.ts` with `import "server-only"`. Auth handled entirely server-side.

**Tech Stack:** Next.js 16 App Router, React Query (queries), Server Actions (mutations), NextAuth v5, TypeScript

---

## Pattern Summary

| Operation | Pattern | Why |
|-----------|---------|-----|
| **Queries** (GET) | API Routes + React Query | Caching, background refetch, stale-while-revalidate |
| **Mutations** (POST/PUT/PATCH/DELETE) | Server Actions + useTransition | Modern Next.js pattern, progressive enhancement |

---

## Summary of Changes

| Current (Anti-Pattern) | Target (Pattern) |
|------------------------|------------------|
| `lib/api.ts` - client-side fetch with auth | Delete |
| `middleware.ts` - `/backend/*` proxy | Delete |
| Browser calls `/backend/*` | Queries: `/api/*`, Mutations: server actions |
| Auth token added client-side | Auth token added server-side |
| No `import "server-only"` enforcement | All API files enforce |
| 6 env vars with fallbacks | 3 env vars, no fallbacks |
| Client-side mutations via fetch | Server Actions with `useTransition` |

---

## Phase 1: Foundation (API Layer)

### Task 1: Create server-only API URL provider

**Files:**
- Create: `ui/src/lib/api/api-url.ts`
- Delete: `ui/src/lib/api/providers/api-url-provider.ts`

**Step 1: Create new file**

```typescript
import "server-only";

/**
 * Backend API URL for server-side requests.
 * Never expose to client - enforced by "server-only" import.
 */
export const API_URL = process.env.BACKEND_URL!;
```

**Step 2: Delete old file**

```bash
rm ui/src/lib/api/providers/api-url-provider.ts
```

**Step 3: Commit**

```bash
git add ui/src/lib/api/api-url.ts
git rm ui/src/lib/api/providers/api-url-provider.ts
git commit -m "refactor(ui): create server-only API URL provider"
```

---

### Task 2: Create server-only authenticated fetch

**Files:**
- Create: `ui/src/lib/api/api-fetch.ts`
- Delete: `ui/src/lib/api/providers/authenticated-fetch-provider.ts`

**Step 1: Create new file**

```typescript
import "server-only";

import { auth } from "@/auth";
import { redirect } from "next/navigation";
import { API_URL } from "./api-url";

/**
 * Server-side authenticated fetch.
 * Use in API route handlers and server components only.
 */
export async function apiFetch(
  path: string,
  options: RequestInit = {}
): Promise<Response> {
  const session = await auth();

  if (!session?.accessToken) {
    redirect("/login");
  }

  const headers = new Headers(options.headers);
  headers.set("Content-Type", "application/json");
  headers.set("Accept", "application/json");
  headers.set("Authorization", `Bearer ${session.accessToken}`);

  const url = path.startsWith("http") ? path : `${API_URL}${path}`;

  const response = await fetch(url, {
    ...options,
    headers,
  });

  if (response.status === 401) {
    redirect("/login");
  }

  return response;
}

/**
 * Server-side unauthenticated fetch for public endpoints.
 */
export async function publicFetch(
  path: string,
  options: RequestInit = {}
): Promise<Response> {
  const headers = new Headers(options.headers);
  headers.set("Content-Type", "application/json");
  headers.set("Accept", "application/json");

  const url = path.startsWith("http") ? path : `${API_URL}${path}`;

  return fetch(url, {
    ...options,
    headers,
  });
}
```

**Step 2: Delete old file and providers directory**

```bash
rm ui/src/lib/api/providers/authenticated-fetch-provider.ts
rmdir ui/src/lib/api/providers
```

**Step 3: Commit**

```bash
git add ui/src/lib/api/api-fetch.ts
git rm -r ui/src/lib/api/providers/
git commit -m "refactor(ui): create server-only authenticated fetch"
```

---

### Task 3: Create tickets API layer

**Files:**
- Create: `ui/src/lib/api/tickets-api.ts`

**Step 1: Create file**

```typescript
import "server-only";

import { apiFetch } from "./api-fetch";

export interface Ticket {
  id: string;
  threadLink?: string;
  openedAt?: string;
  resolvedAt?: string;
  team?: { name: string } | null;
  escalations?: Array<{
    id: string;
    team?: { name: string } | null;
    [key: string]: unknown;
  }>;
  [key: string]: unknown;
}

export interface PaginatedTickets {
  page: number;
  totalPages: number;
  totalElements: number;
  content: Ticket[];
}

interface BackendTeam {
  label?: string;
  code?: string;
  types?: string[];
}

function mapTicket(ticket: Record<string, unknown>): Ticket {
  const team = ticket.team as BackendTeam | null;
  const escalations = ticket.escalations as Array<{ team?: BackendTeam; [key: string]: unknown }> | undefined;

  return {
    ...ticket,
    id: String(ticket.id),
    team: team ? { name: team.code || team.label || "" } : null,
    escalations: escalations?.map((esc) => ({
      ...esc,
      id: String(esc.id),
      team: esc.team ? { name: esc.team.code || esc.team.label || "" } : null,
    })) ?? [],
  };
}

export async function fetchTickets(
  page = 0,
  pageSize = 50,
  dateFrom?: string,
  dateTo?: string
): Promise<PaginatedTickets> {
  const params = new URLSearchParams({
    page: page.toString(),
    pageSize: pageSize.toString(),
  });
  if (dateFrom) params.append("dateFrom", dateFrom);
  if (dateTo) params.append("dateTo", dateTo);

  const response = await apiFetch(`/ticket?${params}`);
  if (!response.ok) {
    throw new Error(`Failed to fetch tickets: ${response.status}`);
  }

  const data = await response.json();
  return {
    ...data,
    content: data.content?.map(mapTicket) ?? [],
  };
}

export async function fetchTicket(id: string): Promise<Ticket> {
  const response = await apiFetch(`/ticket/${id}`);
  if (!response.ok) {
    throw new Error(`Failed to fetch ticket: ${response.status}`);
  }

  const data = await response.json();
  return mapTicket(data);
}

```

**Step 2: Commit**

```bash
git add ui/src/lib/api/tickets-api.ts
git commit -m "feat(ui): add server-only tickets API layer"
```

---

### Task 4: Create teams API layer

**Files:**
- Create: `ui/src/lib/api/teams-api.ts`

**Step 1: Create file**

```typescript
import "server-only";

import { apiFetch } from "./api-fetch";

export interface Team {
  name: string;
  types?: string[];
}

interface BackendTeam {
  label?: string;
  code?: string;
  types?: string[];
}

function mapTeam(team: BackendTeam): Team {
  return {
    name: team.code || team.label || "",
    types: team.types,
  };
}

export async function fetchEscalationTeams(): Promise<Team[]> {
  const response = await apiFetch("/team?type=escalation");
  if (!response.ok) {
    throw new Error(`Failed to fetch escalation teams: ${response.status}`);
  }

  const data = await response.json();
  return data.map(mapTeam);
}

export async function fetchTenantTeams(): Promise<Team[]> {
  const response = await apiFetch("/team?type=tenant");
  if (!response.ok) {
    throw new Error(`Failed to fetch tenant teams: ${response.status}`);
  }

  const data = await response.json();
  return data.map(mapTeam);
}
```

**Step 2: Commit**

```bash
git add ui/src/lib/api/teams-api.ts
git commit -m "feat(ui): add server-only teams API layer"
```

---

### Task 5: Create escalations API layer

**Files:**
- Create: `ui/src/lib/api/escalations-api.ts`

**Step 1: Create file**

```typescript
import "server-only";

import { apiFetch } from "./api-fetch";

export interface Escalation {
  id: string;
  ticketId: string;
  threadLink?: string;
  openedAt?: string;
  resolvedAt?: string;
  escalatingTeam?: string;
  team?: { name: string } | null;
  tags?: string[];
  impact?: string | null;
}

export interface PaginatedEscalations {
  page: number;
  totalPages: number;
  totalElements: number;
  content: Escalation[];
}

interface BackendTeam {
  label?: string;
  code?: string;
}

export async function fetchEscalations(
  page = 0,
  pageSize = 50
): Promise<PaginatedEscalations> {
  const response = await apiFetch(
    `/escalation?page=${page}&pageSize=${pageSize}&escalated=true`
  );
  if (!response.ok) {
    throw new Error(`Failed to fetch escalations: ${response.status}`);
  }

  const data = await response.json();
  return {
    page: data.page,
    totalPages: data.totalPages,
    totalElements: data.totalElements,
    content: data.content.map((e: Record<string, unknown>) => {
      const team = e.team as BackendTeam | null;
      const id = typeof e.id === "object" ? (e.id as { id: unknown }).id : e.id;
      const ticketId = typeof e.ticketId === "object"
        ? (e.ticketId as { id: unknown }).id
        : e.ticketId;

      return {
        id: String(id),
        ticketId: String(ticketId),
        threadLink: e.threadLink,
        openedAt: e.openedAt,
        resolvedAt: e.resolvedAt,
        escalatingTeam: e.escalatingTeam,
        team: team ? { name: team.code || team.label || "" } : null,
        tags: e.tags ?? [],
        impact: e.impact ?? null,
      };
    }),
  };
}
```

**Step 2: Commit**

```bash
git add ui/src/lib/api/escalations-api.ts
git commit -m "feat(ui): add server-only escalations API layer"
```

---

### Task 6: Create dashboard API layer

**Files:**
- Create: `ui/src/lib/api/dashboard-api.ts`

**Step 1: Create file**

```typescript
import "server-only";

import { apiFetch } from "./api-fetch";

function buildParams(dateFrom?: string, dateTo?: string): string {
  const params = new URLSearchParams();
  if (dateFrom) params.append("dateFrom", dateFrom);
  if (dateTo) params.append("dateTo", dateTo);
  const str = params.toString();
  return str ? `?${str}` : "";
}

// Response SLA
export async function fetchFirstResponseDistribution(
  dateFrom?: string,
  dateTo?: string
): Promise<number[]> {
  const response = await apiFetch(
    `/dashboard/first-response-distribution${buildParams(dateFrom, dateTo)}`
  );
  if (!response.ok) throw new Error(`Failed: ${response.status}`);
  return response.json();
}

export async function fetchFirstResponsePercentiles(
  dateFrom?: string,
  dateTo?: string
): Promise<{ p50: number; p90: number }> {
  const response = await apiFetch(
    `/dashboard/first-response-percentiles${buildParams(dateFrom, dateTo)}`
  );
  if (!response.ok) throw new Error(`Failed: ${response.status}`);
  return response.json();
}

export async function fetchUnattendedQueriesCount(
  dateFrom?: string,
  dateTo?: string
): Promise<{ count: number }> {
  const response = await apiFetch(
    `/dashboard/unattended-queries-count${buildParams(dateFrom, dateTo)}`
  );
  if (!response.ok) throw new Error(`Failed: ${response.status}`);
  return response.json();
}

// Resolution SLA
export async function fetchResolutionPercentiles(
  dateFrom?: string,
  dateTo?: string
): Promise<{ p50: number; p75: number; p90: number }> {
  const response = await apiFetch(
    `/dashboard/resolution-percentiles${buildParams(dateFrom, dateTo)}`
  );
  if (!response.ok) throw new Error(`Failed: ${response.status}`);
  return response.json();
}

export async function fetchResolutionDurationDistribution(
  dateFrom?: string,
  dateTo?: string
): Promise<{ label: string; count: number; minMinutes: number; maxMinutes: number }[]> {
  const response = await apiFetch(
    `/dashboard/resolution-duration-distribution${buildParams(dateFrom, dateTo)}`
  );
  if (!response.ok) throw new Error(`Failed: ${response.status}`);
  return response.json();
}

export async function fetchResolutionTimesByWeek(
  dateFrom?: string,
  dateTo?: string
): Promise<{ week: string; p50: number; p75: number; p90: number }[]> {
  const response = await apiFetch(
    `/dashboard/resolution-times-by-week${buildParams(dateFrom, dateTo)}`
  );
  if (!response.ok) throw new Error(`Failed: ${response.status}`);
  return response.json();
}

export async function fetchUnresolvedTicketAges(
  dateFrom?: string,
  dateTo?: string
): Promise<{ p50: string; p90: string }> {
  const response = await apiFetch(
    `/dashboard/unresolved-ticket-ages${buildParams(dateFrom, dateTo)}`
  );
  if (!response.ok) throw new Error(`Failed: ${response.status}`);
  return response.json();
}

export async function fetchIncomingVsResolvedRate(
  dateFrom?: string,
  dateTo?: string
): Promise<{ time: string; incoming: number; resolved: number }[]> {
  const response = await apiFetch(
    `/dashboard/incoming-vs-resolved-rate${buildParams(dateFrom, dateTo)}`
  );
  if (!response.ok) throw new Error(`Failed: ${response.status}`);
  return response.json();
}

// Escalation SLA
export async function fetchAvgEscalationDurationByTag(
  dateFrom?: string,
  dateTo?: string
): Promise<{ tag: string; avgDuration: number }[]> {
  const response = await apiFetch(
    `/dashboard/avg-escalation-duration-by-tag${buildParams(dateFrom, dateTo)}`
  );
  if (!response.ok) throw new Error(`Failed: ${response.status}`);
  return response.json();
}

export async function fetchEscalationPercentageByTag(
  dateFrom?: string,
  dateTo?: string
): Promise<{ tag: string; count: number }[]> {
  const response = await apiFetch(
    `/dashboard/escalation-percentage-by-tag${buildParams(dateFrom, dateTo)}`
  );
  if (!response.ok) throw new Error(`Failed: ${response.status}`);
  return response.json();
}

export async function fetchEscalationTrendsByDate(
  dateFrom?: string,
  dateTo?: string
): Promise<{ date: string; escalations: number }[]> {
  const response = await apiFetch(
    `/dashboard/escalation-trends-by-date${buildParams(dateFrom, dateTo)}`
  );
  if (!response.ok) throw new Error(`Failed: ${response.status}`);
  return response.json();
}

export async function fetchEscalationsByTeam(
  dateFrom?: string,
  dateTo?: string
): Promise<{ assigneeName: string; totalEscalations: number }[]> {
  const response = await apiFetch(
    `/dashboard/escalations-by-team${buildParams(dateFrom, dateTo)}`
  );
  if (!response.ok) throw new Error(`Failed: ${response.status}`);
  return response.json();
}

export async function fetchEscalationsByImpact(
  dateFrom?: string,
  dateTo?: string
): Promise<{ impactLevel: string; totalEscalations: number }[]> {
  const response = await apiFetch(
    `/dashboard/escalations-by-impact${buildParams(dateFrom, dateTo)}`
  );
  if (!response.ok) throw new Error(`Failed: ${response.status}`);
  return response.json();
}

// Weekly Trends
export async function fetchWeeklyTicketCounts(): Promise<
  { week: string; opened: number; closed: number; escalated: number; stale: number }[]
> {
  const response = await apiFetch("/dashboard/weekly-ticket-counts");
  if (!response.ok) throw new Error(`Failed: ${response.status}`);
  return response.json();
}

export async function fetchWeeklyComparison(): Promise<
  { label: string; thisWeek: number; lastWeek: number; change: number }[]
> {
  const response = await apiFetch("/dashboard/weekly-comparison");
  if (!response.ok) throw new Error(`Failed: ${response.status}`);
  return response.json();
}

export async function fetchTopEscalatedTagsThisWeek(): Promise<
  { tag: string; count: number }[]
> {
  const response = await apiFetch("/dashboard/top-escalated-tags-this-week");
  if (!response.ok) throw new Error(`Failed: ${response.status}`);
  return response.json();
}

export async function fetchResolutionTimeByTag(
  dateFrom?: string,
  dateTo?: string
): Promise<{ tag: string; p50: number; p90: number }[]> {
  const response = await apiFetch(
    `/dashboard/resolution-time-by-tag${buildParams(dateFrom, dateTo)}`
  );
  if (!response.ok) throw new Error(`Failed: ${response.status}`);
  return response.json();
}
```

**Step 2: Commit**

```bash
git add ui/src/lib/api/dashboard-api.ts
git commit -m "feat(ui): add server-only dashboard API layer"
```

---

### Task 7: Create users API layer

**Files:**
- Create: `ui/src/lib/api/users-api.ts`

**Step 1: Create file**

```typescript
import "server-only";

import { apiFetch } from "./api-fetch";

export interface SupportMember {
  id: string;
  name: string;
  email?: string;
}

export async function fetchSupportMembers(): Promise<SupportMember[]> {
  const response = await apiFetch("/user/support");
  if (!response.ok) {
    throw new Error(`Failed to fetch support members: ${response.status}`);
  }
  return response.json();
}

export async function fetchAssignmentEnabled(): Promise<boolean> {
  const response = await apiFetch("/assignment/enabled");
  if (!response.ok) {
    throw new Error(`Failed to fetch assignment status: ${response.status}`);
  }
  const data = await response.json();
  return data.enabled;
}
```

**Step 2: Commit**

```bash
git add ui/src/lib/api/users-api.ts
git commit -m "feat(ui): add server-only users API layer"
```

---

### Task 8: Create stats API layer

**Files:**
- Create: `ui/src/lib/api/stats-api.ts`

**Step 1: Create file**

```typescript
import "server-only";

import { apiFetch } from "./api-fetch";

export interface RatingWeekly {
  weekStart: string;
  average: number | null;
  count: number | null;
}

export interface RatingsResult {
  average: number | null;
  count: number | null;
  weekly?: RatingWeekly[];
}

export async function fetchRatings(
  from?: string,
  to?: string
): Promise<RatingsResult> {
  const request: Record<string, unknown> = { type: "ticket-ratings" };
  if (from) request.from = from;
  if (to) request.to = to;

  const response = await apiFetch("/stats", {
    method: "POST",
    body: JSON.stringify([request]),
  });

  if (!response.ok) {
    throw new Error(`Failed to fetch ratings: ${response.status}`);
  }

  const data = await response.json();
  const first = data?.[0];

  if (first?.values) {
    const values = first.values as RatingsResult;
    const rootWeekly = first.weekly;
    return {
      average: values.average ?? null,
      count: values.count ?? null,
      weekly: Array.isArray(values.weekly)
        ? values.weekly
        : Array.isArray(rootWeekly)
          ? rootWeekly
          : undefined,
    };
  }

  return { average: null, count: null };
}

export interface Registry {
  impacts: string[];
  tags: string[];
}

export async function fetchRegistry(): Promise<Registry> {
  const [impactsRes, tagsRes] = await Promise.all([
    apiFetch("/registry/impact"),
    apiFetch("/registry/tag"),
  ]);

  if (!impactsRes.ok || !tagsRes.ok) {
    throw new Error("Failed to fetch registry");
  }

  return {
    impacts: await impactsRes.json(),
    tags: await tagsRes.json(),
  };
}
```

**Step 2: Commit**

```bash
git add ui/src/lib/api/stats-api.ts
git commit -m "feat(ui): add server-only stats API layer"
```

---

### Task 9: Create auth API layer

**Files:**
- Create: `ui/src/lib/api/auth-api.ts`

**Step 1: Create file**

```typescript
import "server-only";

import { apiFetch, publicFetch } from "./api-fetch";
import { API_URL } from "./api-url";

/**
 * Call backend logout endpoint.
 */
export async function backendLogout(): Promise<void> {
  try {
    await apiFetch("/auth/logout", { method: "POST" });
  } catch {
    // Ignore logout errors
  }
}

/**
 * Get OAuth authorization URL for redirect.
 */
export function getOAuthUrl(provider: "google" | "azure"): string {
  return `${API_URL}/oauth2/authorization/${provider}`;
}

/**
 * Exchange auth code for token.
 */
export async function exchangeCodeForToken(
  code: string
): Promise<{ token: string } | null> {
  const response = await publicFetch("/auth/token", {
    method: "POST",
    body: JSON.stringify({ code }),
  });

  if (!response.ok) {
    return null;
  }

  return response.json();
}

/**
 * Fetch user data with token.
 */
export async function fetchUserWithToken(
  token: string
): Promise<Record<string, unknown> | null> {
  const response = await fetch(`${API_URL}/auth/me`, {
    headers: { Authorization: `Bearer ${token}` },
  });

  if (!response.ok) {
    return null;
  }

  return response.json();
}
```

**Step 2: Commit**

```bash
git add ui/src/lib/api/auth-api.ts
git commit -m "feat(ui): add server-only auth API layer"
```

---

### Task 10: Create API layer index

**Files:**
- Create: `ui/src/lib/api/index.ts`

**Step 1: Create file**

```typescript
// Re-export all API functions for convenient imports
// All files in this directory use "server-only"

export * from "./api-fetch";
export * from "./tickets-api";
export * from "./teams-api";
export * from "./escalations-api";
export * from "./dashboard-api";
export * from "./users-api";
export * from "./stats-api";
export * from "./auth-api";
```

**Step 2: Commit**

```bash
git add ui/src/lib/api/index.ts
git commit -m "feat(ui): add API layer index"
```

---

## Phase 2: API Route Handlers

### Task 11: Create tickets API routes

**Files:**
- Create: `ui/src/app/api/tickets/route.ts`
- Create: `ui/src/app/api/tickets/[id]/route.ts`

**Step 1: Create list route**

```typescript
// ui/src/app/api/tickets/route.ts
import { NextRequest, NextResponse } from "next/server";
import { fetchTickets } from "@/lib/api/tickets-api";

export async function GET(request: NextRequest) {
  const searchParams = request.nextUrl.searchParams;
  const page = parseInt(searchParams.get("page") ?? "0");
  const pageSize = parseInt(searchParams.get("pageSize") ?? "50");
  const dateFrom = searchParams.get("dateFrom") ?? undefined;
  const dateTo = searchParams.get("dateTo") ?? undefined;

  try {
    const data = await fetchTickets(page, pageSize, dateFrom, dateTo);
    return NextResponse.json(data);
  } catch (error) {
    const message = error instanceof Error ? error.message : "Unknown error";
    return NextResponse.json({ error: message }, { status: 500 });
  }
}
```

**Step 2: Create single ticket route (GET only - mutations via server actions)**

```typescript
// ui/src/app/api/tickets/[id]/route.ts
import { NextRequest, NextResponse } from "next/server";
import { fetchTicket } from "@/lib/api/tickets-api";

export async function GET(
  _request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;

  try {
    const data = await fetchTicket(id);
    return NextResponse.json(data);
  } catch (error) {
    const message = error instanceof Error ? error.message : "Unknown error";
    return NextResponse.json({ error: message }, { status: 500 });
  }
}
```

**Step 3: Commit**

```bash
git add ui/src/app/api/tickets/
git commit -m "feat(ui): add tickets API route handlers"
```

---

### Task 12: Create teams API route

**Files:**
- Create: `ui/src/app/api/teams/route.ts`

**Step 1: Create file**

```typescript
import { NextRequest, NextResponse } from "next/server";
import { fetchEscalationTeams, fetchTenantTeams } from "@/lib/api/teams-api";

export async function GET(request: NextRequest) {
  const type = request.nextUrl.searchParams.get("type");

  try {
    if (type === "escalation") {
      const data = await fetchEscalationTeams();
      return NextResponse.json(data);
    }
    if (type === "tenant") {
      const data = await fetchTenantTeams();
      return NextResponse.json(data);
    }
    return NextResponse.json({ error: "Invalid type parameter" }, { status: 400 });
  } catch (error) {
    const message = error instanceof Error ? error.message : "Unknown error";
    return NextResponse.json({ error: message }, { status: 500 });
  }
}
```

**Step 2: Commit**

```bash
git add ui/src/app/api/teams/route.ts
git commit -m "feat(ui): add teams API route handler"
```

---

### Task 13: Create escalations API route

**Files:**
- Create: `ui/src/app/api/escalations/route.ts`

**Step 1: Create file**

```typescript
import { NextRequest, NextResponse } from "next/server";
import { fetchEscalations } from "@/lib/api/escalations-api";

export async function GET(request: NextRequest) {
  const searchParams = request.nextUrl.searchParams;
  const page = parseInt(searchParams.get("page") ?? "0");
  const pageSize = parseInt(searchParams.get("pageSize") ?? "50");

  try {
    const data = await fetchEscalations(page, pageSize);
    return NextResponse.json(data);
  } catch (error) {
    const message = error instanceof Error ? error.message : "Unknown error";
    return NextResponse.json({ error: message }, { status: 500 });
  }
}
```

**Step 2: Commit**

```bash
git add ui/src/app/api/escalations/route.ts
git commit -m "feat(ui): add escalations API route handler"
```

---

### Task 14: Create dashboard API routes

**Files:**
- Create: `ui/src/app/api/dashboard/[endpoint]/route.ts`

**Step 1: Create dynamic route**

```typescript
import { NextRequest, NextResponse } from "next/server";
import * as dashboardApi from "@/lib/api/dashboard-api";

const ENDPOINT_MAP: Record<string, (dateFrom?: string, dateTo?: string) => Promise<unknown>> = {
  "first-response-distribution": dashboardApi.fetchFirstResponseDistribution,
  "first-response-percentiles": dashboardApi.fetchFirstResponsePercentiles,
  "unattended-queries-count": dashboardApi.fetchUnattendedQueriesCount,
  "resolution-percentiles": dashboardApi.fetchResolutionPercentiles,
  "resolution-duration-distribution": dashboardApi.fetchResolutionDurationDistribution,
  "resolution-times-by-week": dashboardApi.fetchResolutionTimesByWeek,
  "unresolved-ticket-ages": dashboardApi.fetchUnresolvedTicketAges,
  "incoming-vs-resolved-rate": dashboardApi.fetchIncomingVsResolvedRate,
  "avg-escalation-duration-by-tag": dashboardApi.fetchAvgEscalationDurationByTag,
  "escalation-percentage-by-tag": dashboardApi.fetchEscalationPercentageByTag,
  "escalation-trends-by-date": dashboardApi.fetchEscalationTrendsByDate,
  "escalations-by-team": dashboardApi.fetchEscalationsByTeam,
  "escalations-by-impact": dashboardApi.fetchEscalationsByImpact,
  "weekly-ticket-counts": dashboardApi.fetchWeeklyTicketCounts,
  "weekly-comparison": dashboardApi.fetchWeeklyComparison,
  "top-escalated-tags-this-week": dashboardApi.fetchTopEscalatedTagsThisWeek,
  "resolution-time-by-tag": dashboardApi.fetchResolutionTimeByTag,
};

export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ endpoint: string }> }
) {
  const { endpoint } = await params;
  const handler = ENDPOINT_MAP[endpoint];

  if (!handler) {
    return NextResponse.json({ error: "Unknown endpoint" }, { status: 404 });
  }

  const searchParams = request.nextUrl.searchParams;
  const dateFrom = searchParams.get("dateFrom") ?? undefined;
  const dateTo = searchParams.get("dateTo") ?? undefined;

  try {
    const data = await handler(dateFrom, dateTo);
    return NextResponse.json(data);
  } catch (error) {
    const message = error instanceof Error ? error.message : "Unknown error";
    return NextResponse.json({ error: message }, { status: 500 });
  }
}
```

**Step 2: Commit**

```bash
git add ui/src/app/api/dashboard/
git commit -m "feat(ui): add dashboard API route handlers"
```

---

### Task 15: Create users API routes

**Files:**
- Create: `ui/src/app/api/users/support/route.ts`
- Create: `ui/src/app/api/assignment/enabled/route.ts`

**Step 1: Create support members route**

```typescript
// ui/src/app/api/users/support/route.ts
import { NextResponse } from "next/server";
import { fetchSupportMembers } from "@/lib/api/users-api";

export async function GET() {
  try {
    const data = await fetchSupportMembers();
    return NextResponse.json(data);
  } catch (error) {
    const message = error instanceof Error ? error.message : "Unknown error";
    return NextResponse.json({ error: message }, { status: 500 });
  }
}
```

**Step 2: Create assignment enabled route**

```typescript
// ui/src/app/api/assignment/enabled/route.ts
import { NextResponse } from "next/server";
import { fetchAssignmentEnabled } from "@/lib/api/users-api";

export async function GET() {
  try {
    const enabled = await fetchAssignmentEnabled();
    return NextResponse.json({ enabled });
  } catch (error) {
    const message = error instanceof Error ? error.message : "Unknown error";
    return NextResponse.json({ error: message }, { status: 500 });
  }
}
```

**Step 3: Commit**

```bash
git add ui/src/app/api/users/ ui/src/app/api/assignment/
git commit -m "feat(ui): add users and assignment API route handlers"
```

---

### Task 16: Create stats API routes

**Files:**
- Create: `ui/src/app/api/stats/ratings/route.ts`
- Create: `ui/src/app/api/registry/route.ts`

**Step 1: Create ratings route**

```typescript
// ui/src/app/api/stats/ratings/route.ts
import { NextRequest, NextResponse } from "next/server";
import { fetchRatings } from "@/lib/api/stats-api";

export async function GET(request: NextRequest) {
  const searchParams = request.nextUrl.searchParams;
  const from = searchParams.get("from") ?? undefined;
  const to = searchParams.get("to") ?? undefined;

  try {
    const data = await fetchRatings(from, to);
    return NextResponse.json(data);
  } catch (error) {
    const message = error instanceof Error ? error.message : "Unknown error";
    return NextResponse.json({ error: message }, { status: 500 });
  }
}
```

**Step 2: Create registry route**

```typescript
// ui/src/app/api/registry/route.ts
import { NextResponse } from "next/server";
import { fetchRegistry } from "@/lib/api/stats-api";

export async function GET() {
  try {
    const data = await fetchRegistry();
    return NextResponse.json(data);
  } catch (error) {
    const message = error instanceof Error ? error.message : "Unknown error";
    return NextResponse.json({ error: message }, { status: 500 });
  }
}
```

**Step 3: Commit**

```bash
git add ui/src/app/api/stats/ ui/src/app/api/registry/
git commit -m "feat(ui): add stats and registry API route handlers"
```

---

### Task 17: Create OAuth redirect API route

**Files:**
- Create: `ui/src/app/api/oauth/[provider]/route.ts`

**Step 1: Create file**

```typescript
import { NextRequest, NextResponse } from "next/server";
import { getOAuthUrl } from "@/lib/api/auth-api";

export async function GET(
  _request: NextRequest,
  { params }: { params: Promise<{ provider: string }> }
) {
  const { provider } = await params;

  if (provider !== "google" && provider !== "azure") {
    return NextResponse.json({ error: "Invalid provider" }, { status: 400 });
  }

  const url = getOAuthUrl(provider);
  return NextResponse.redirect(url);
}
```

**Step 2: Commit**

```bash
git add ui/src/app/api/oauth/
git commit -m "feat(ui): add OAuth redirect API route"
```

---

---

## Phase 3: Server Actions (Mutations)

### Task 19: Create tickets server actions

**Files:**
- Create: `ui/src/lib/server-actions/tickets-actions.ts`

**Step 1: Create file**

```typescript
"use server";

import { revalidatePath } from "next/cache";
import { apiFetch } from "@/lib/api/api-fetch";

export interface TicketUpdate {
  team?: { name: string };
  impact?: string;
  tags?: string[];
  [key: string]: unknown;
}

export async function updateTicket(
  id: string,
  updates: TicketUpdate
): Promise<{ success: boolean; error?: string }> {
  try {
    const response = await apiFetch(`/ticket/${id}`, {
      method: "PATCH",
      body: JSON.stringify(updates),
    });

    if (!response.ok) {
      const error = await response.text();
      return { success: false, error };
    }

    // Revalidate ticket pages
    revalidatePath("/");
    revalidatePath(`/tickets/${id}`);

    return { success: true };
  } catch (error) {
    return {
      success: false,
      error: error instanceof Error ? error.message : "Unknown error",
    };
  }
}
```

**Step 2: Commit**

```bash
git add ui/src/lib/server-actions/tickets-actions.ts
git commit -m "feat(ui): add tickets server actions for mutations"
```

---

### Task 20: Create auth server actions

**Files:**
- Create: `ui/src/lib/server-actions/auth-actions.ts`

**Step 1: Create file**

```typescript
"use server";

import { signOut } from "@/auth";
import { backendLogout } from "@/lib/api/auth-api";

export async function logout(): Promise<void> {
  await backendLogout();
  await signOut({ redirectTo: "/login" });
}
```

**Step 2: Commit**

```bash
git add ui/src/lib/server-actions/auth-actions.ts
git commit -m "feat(ui): add auth server actions"
```

---

### Task 21: Create server actions index

**Files:**
- Create: `ui/src/lib/server-actions/index.ts`

**Step 1: Create file**

```typescript
// Re-export all server actions
export * from "./tickets-actions";
export * from "./auth-actions";
```

**Step 2: Commit**

```bash
git add ui/src/lib/server-actions/index.ts
git commit -m "feat(ui): add server actions index"
```

---

## Phase 4: Migration

### Task 22: Update client hooks to use new API routes

**Files:**
- Modify: `ui/src/lib/hooks/backend.ts`
- Modify: `ui/src/lib/hooks/dashboard.ts`

**Step 1: Update backend.ts**

Replace all `apiGet`/`apiPost` imports and calls with fetch to `/api/*`:

```typescript
// ui/src/lib/hooks/backend.ts
import { useQuery } from '@tanstack/react-query'
import type {
  PaginatedTickets,
  PaginatedEscalations,
  EscalationTeam,
  SupportMember,
  TicketWithLogs,
  Escalation,
} from '@/lib/types'

async function apiGet<T>(path: string): Promise<T> {
  const res = await fetch(`/api${path}`);
  if (!res.ok) {
    if (res.status === 401) {
      window.location.href = `/login?callbackUrl=${encodeURIComponent(window.location.pathname)}`;
    }
    throw new Error(`API error: ${res.status}`);
  }
  return res.json();
}

async function apiPost<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(`/api${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    if (res.status === 401) {
      window.location.href = `/login?callbackUrl=${encodeURIComponent(window.location.pathname)}`;
    }
    throw new Error(`API error: ${res.status}`);
  }
  return res.json();
}

// ===== Ticket Hooks =====

export function useTickets(page: number = 0, pageSize: number = 50, from?: string, to?: string) {
  return useQuery<PaginatedTickets>({
    queryKey: ['tickets', page, pageSize, from, to],
    queryFn: async () => {
      const params = new URLSearchParams({
        page: page.toString(),
        pageSize: pageSize.toString()
      })
      if (from) params.append('dateFrom', from)
      if (to) params.append('dateTo', to)
      return apiGet(`/tickets?${params}`)
    }
  })
}

export function useAllTickets(pageSize: number = 200, from?: string, to?: string, enabled = true) {
  return useQuery<PaginatedTickets>({
    queryKey: ['tickets', 'all', pageSize, from, to],
    enabled,
    queryFn: async () => {
      const params = new URLSearchParams({
        page: '0',
        pageSize: pageSize.toString()
      })
      if (from) params.append('dateFrom', from)
      if (to) params.append('dateTo', to)

      const first = await apiGet<PaginatedTickets>(`/tickets?${params}`)
      const totalPages = first.totalPages ?? 1

      if (totalPages <= 1) return first;

      const pagesToFetch = []
      for (let p = 1; p < totalPages; p++) {
        const pageParams = new URLSearchParams({
          page: p.toString(),
          pageSize: pageSize.toString()
        })
        if (from) pageParams.append('dateFrom', from)
        if (to) pageParams.append('dateTo', to)
        pagesToFetch.push(apiGet<PaginatedTickets>(`/tickets?${pageParams}`))
      }

      const rest = await Promise.all(pagesToFetch)
      const allContent = [
        ...(first.content || []),
        ...rest.flatMap(res => res.content || []),
      ] as TicketWithLogs[]

      return {
        page: 0,
        totalPages,
        totalElements: allContent.length,
        content: allContent,
      }
    }
  })
}

export function useTicket(id: string | undefined) {
  return useQuery({
    queryKey: ['ticket', id],
    queryFn: () => apiGet(`/tickets/${id}`),
    enabled: !!id,
  })
}

// ===== Team Hooks =====

export function useEscalationTeams(enabled: boolean = true) {
  return useQuery<EscalationTeam[]>({
    queryKey: ['team', 'escalation'],
    enabled,
    queryFn: () => apiGet('/teams?type=escalation'),
  })
}

export function useTenantTeams() {
  return useQuery({
    queryKey: ['team', 'tenant'],
    queryFn: () => apiGet('/teams?type=tenant'),
  })
}

// ===== User Hooks =====

export function useSupportMembers() {
  return useQuery<SupportMember[]>({
    queryKey: ['user', 'support'],
    queryFn: () => apiGet('/users/support'),
  })
}

// ===== Assignment Hooks =====

export function useAssignmentEnabled() {
  return useQuery<boolean>({
    queryKey: ['assignment', 'enabled'],
    queryFn: async () => {
      const response = await apiGet<{ enabled: boolean }>('/assignment/enabled')
      return response.enabled
    },
    staleTime: 5 * 60 * 1000,
  })
}

// ===== Escalation Hooks =====

export function useEscalations(page: number = 0, pageSize: number = 50) {
  return useQuery<PaginatedEscalations>({
    queryKey: ['escalations', page, pageSize],
    queryFn: () => apiGet(`/escalations?page=${page}&pageSize=${pageSize}`),
  })
}

// ===== Registry & Ratings Hooks =====

export type RatingWeekly = { weekStart: string; average: number | null; count: number | null }
export type RatingsResult = { average: number | null; count: number | null; weekly?: RatingWeekly[] }

export const useRatings = (from?: string, to?: string) => {
  return useQuery({
    queryKey: ['ratings', from, to],
    queryFn: async (): Promise<RatingsResult> => {
      const params = new URLSearchParams()
      if (from) params.append('from', from)
      if (to) params.append('to', to)
      const query = params.toString()
      return apiGet(`/stats/ratings${query ? `?${query}` : ''}`)
    },
  })
}

export function useRegistry() {
  return useQuery({
    queryKey: ['registry'],
    queryFn: () => apiGet('/registry'),
  })
}
```

**Step 2: Update dashboard.ts**

```typescript
// ui/src/lib/hooks/dashboard.ts
import { useQuery } from '@tanstack/react-query'

async function apiGet<T>(path: string): Promise<T> {
  const res = await fetch(`/api${path}`);
  if (!res.ok) {
    if (res.status === 401) {
      window.location.href = `/login?callbackUrl=${encodeURIComponent(window.location.pathname)}`;
    }
    throw new Error(`API error: ${res.status}`);
  }
  return res.json();
}

function buildParams(dateFrom?: string, dateTo?: string): string {
  const params = new URLSearchParams()
  if (dateFrom) params.append('dateFrom', dateFrom)
  if (dateTo) params.append('dateTo', dateTo)
  const queryString = params.toString()
  return queryString ? `?${queryString}` : ''
}

// ===== Response SLA Hooks =====

export function useFirstResponseDurationDistribution(enabled = true, dateFrom?: string, dateTo?: string) {
  return useQuery<number[]>({
    queryKey: ['firstResponseDurationDistribution', dateFrom, dateTo],
    queryFn: () => apiGet(`/dashboard/first-response-distribution${buildParams(dateFrom, dateTo)}`),
    enabled,
  })
}

export function useFirstResponsePercentiles(enabled = true, dateFrom?: string, dateTo?: string) {
  return useQuery<{ p50: number; p90: number }>({
    queryKey: ['firstResponsePercentiles', dateFrom, dateTo],
    queryFn: () => apiGet(`/dashboard/first-response-percentiles${buildParams(dateFrom, dateTo)}`),
    enabled,
  })
}

export function useUnattendedQueriesCount(enabled = true, dateFrom?: string, dateTo?: string) {
  return useQuery<{ count: number }>({
    queryKey: ['unattendedQueriesCount', dateFrom, dateTo],
    queryFn: () => apiGet(`/dashboard/unattended-queries-count${buildParams(dateFrom, dateTo)}`),
    enabled,
  })
}

export type ResolutionDurationBucket = { label: string; count: number; minMinutes: number; maxMinutes: number }

// ===== Resolution SLA Hooks =====

export function useTicketResolutionPercentiles(enabled = true, dateFrom?: string, dateTo?: string) {
  return useQuery<{ p50: number; p75: number; p90: number }>({
    queryKey: ['ticketResolutionPercentiles', dateFrom, dateTo],
    queryFn: () => apiGet(`/dashboard/resolution-percentiles${buildParams(dateFrom, dateTo)}`),
    enabled,
  })
}

export function useTicketResolutionDurationDistribution(enabled = true, dateFrom?: string, dateTo?: string) {
  return useQuery<ResolutionDurationBucket[], Error>({
    queryKey: ['ticketResolutionDurationDistribution', dateFrom, dateTo],
    queryFn: () => apiGet(`/dashboard/resolution-duration-distribution${buildParams(dateFrom, dateTo)}`),
    enabled,
  })
}

export function useResolutionTimesByWeek(enabled = true, dateFrom?: string, dateTo?: string) {
  return useQuery<{ week: string; p50: number; p75: number; p90: number }[]>({
    queryKey: ['resolutionTimesByWeek', dateFrom, dateTo],
    queryFn: () => apiGet(`/dashboard/resolution-times-by-week${buildParams(dateFrom, dateTo)}`),
    enabled,
  })
}

export function useUnresolvedTicketAges(enabled = true, dateFrom?: string, dateTo?: string) {
  return useQuery<{ p50: string; p90: string }>({
    queryKey: ['unresolvedTicketAges', dateFrom, dateTo],
    queryFn: () => apiGet(`/dashboard/unresolved-ticket-ages${buildParams(dateFrom, dateTo)}`),
    enabled,
  })
}

export function useIncomingVsResolvedRate(enabled = true, dateFrom?: string, dateTo?: string) {
  return useQuery<{ time: string; incoming: number; resolved: number }[]>({
    queryKey: ['incomingVsResolvedRate', dateFrom, dateTo],
    queryFn: () => apiGet(`/dashboard/incoming-vs-resolved-rate${buildParams(dateFrom, dateTo)}`),
    enabled,
  })
}

// ===== Escalation SLA Hooks =====

export function useAvgEscalationDurationByTag(enabled = true, dateFrom?: string, dateTo?: string) {
  return useQuery<{ tag: string; avgDuration: number }[]>({
    queryKey: ['avgEscalationDurationByTag', dateFrom, dateTo],
    queryFn: () => apiGet(`/dashboard/avg-escalation-duration-by-tag${buildParams(dateFrom, dateTo)}`),
    enabled,
  })
}

export function useEscalationPercentageByTag(enabled = true, dateFrom?: string, dateTo?: string) {
  return useQuery<{ tag: string; count: number }[]>({
    queryKey: ['escalationPercentageByTag', dateFrom, dateTo],
    queryFn: () => apiGet(`/dashboard/escalation-percentage-by-tag${buildParams(dateFrom, dateTo)}`),
    enabled,
  })
}

export function useEscalationTrendsByDate(enabled = true, dateFrom?: string, dateTo?: string) {
  return useQuery<{ date: string; escalations: number }[]>({
    queryKey: ['escalationTrendsByDate', dateFrom, dateTo],
    queryFn: () => apiGet(`/dashboard/escalation-trends-by-date${buildParams(dateFrom, dateTo)}`),
    enabled,
  })
}

export function useEscalationsByTeam(enabled = true, dateFrom?: string, dateTo?: string) {
  return useQuery<{ assigneeName: string; totalEscalations: number }[]>({
    queryKey: ['escalationsByTeam', dateFrom, dateTo],
    queryFn: () => apiGet(`/dashboard/escalations-by-team${buildParams(dateFrom, dateTo)}`),
    enabled,
  })
}

export function useEscalationsByImpact(enabled = true, dateFrom?: string, dateTo?: string) {
  return useQuery<{ impactLevel: string; totalEscalations: number }[]>({
    queryKey: ['escalationsByImpact', dateFrom, dateTo],
    queryFn: () => apiGet(`/dashboard/escalations-by-impact${buildParams(dateFrom, dateTo)}`),
    enabled,
  })
}

// ===== Weekly Trends Hooks =====

export function useWeeklyTicketCounts(enabled = true) {
  return useQuery<{ week: string; opened: number; closed: number; escalated: number; stale: number }[]>({
    queryKey: ['weeklyTicketCounts'],
    queryFn: () => apiGet('/dashboard/weekly-ticket-counts'),
    enabled,
  })
}

export function useWeeklyComparison(enabled = true) {
  return useQuery<{ label: string; thisWeek: number; lastWeek: number; change: number }[]>({
    queryKey: ['weeklyComparison'],
    queryFn: () => apiGet('/dashboard/weekly-comparison'),
    enabled,
  })
}

export function useTopEscalatedTagsThisWeek(enabled = true) {
  return useQuery<{ tag: string; count: number }[]>({
    queryKey: ['topEscalatedTagsThisWeek'],
    queryFn: () => apiGet('/dashboard/top-escalated-tags-this-week'),
    enabled,
  })
}

export function useResolutionTimeByTag(enabled = true, startDate?: string, endDate?: string) {
  return useQuery<{ tag: string; p50: number; p90: number }[]>({
    queryKey: ['resolutionTimeByTag', startDate, endDate],
    queryFn: () => apiGet(`/dashboard/resolution-time-by-tag${buildParams(startDate, endDate)}`),
    enabled,
  })
}
```

**Step 3: Commit**

```bash
git add ui/src/lib/hooks/backend.ts ui/src/lib/hooks/dashboard.ts
git commit -m "refactor(ui): update hooks to use /api/* routes"
```

---

### Task 23: Update useAuth hook

**Files:**
- Modify: `ui/src/hooks/useAuth.ts`

**Step 1: Remove accessToken exposure, use server action for logout**

```typescript
"use client";

import { useSession } from "next-auth/react";
import { useMemo, useTransition } from "react";
import type { AuthTeam, AuthUser } from "@/auth.config";
import { logout as logoutAction } from "@/lib/server-actions";

interface UseAuthReturn {
  user: AuthUser | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  isLeadership: boolean;
  isEscalationTeam: boolean;
  isSupportEngineer: boolean;
  actualEscalationTeams: string[];
  logout: () => void;
  isLoggingOut: boolean;
}

export function useAuth(): UseAuthReturn {
  const { data: session, status } = useSession();
  const [isLoggingOut, startLogout] = useTransition();

  const isLoading = status === "loading";
  const isAuthenticated = status === "authenticated" && !!session?.user;
  const user = session?.user ?? null;

  const isLeadership = user?.roles?.includes("leadership") ?? false;
  const isSupportEngineer = user?.roles?.includes("supportEngineer") ?? false;
  const isEscalationTeam = user?.roles?.includes("escalation") ?? false;

  const actualEscalationTeams = useMemo(() => {
    if (!user || !isEscalationTeam) return [];
    return user.teams
      .filter((t: AuthTeam) => t.types.some((type: string) => /escalation/i.test(type)))
      .map((t: AuthTeam) => t.code || t.label);
  }, [user, isEscalationTeam]);

  const logout = () => {
    startLogout(async () => {
      await logoutAction();
    });
  };

  return {
    user,
    isLoading,
    isAuthenticated,
    isLeadership,
    isEscalationTeam,
    isSupportEngineer,
    actualEscalationTeams,
    logout,
    isLoggingOut,
  };
}
```

**Step 2: Commit**

```bash
git add ui/src/hooks/useAuth.ts
git commit -m "refactor(ui): remove accessToken exposure from useAuth"
```

---

### Task 24: Update login page

**Files:**
- Modify: `ui/src/app/login/page.tsx`

**Step 1: Change OAuth to use API route**

Find and replace the `handleLogin` function:

```typescript
const handleLogin = (provider: "google" | "azure") => {
  // OAuth goes through API route - server handles redirect to backend
  const oauthUrl = `/api/oauth/${provider}`;

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
```

Also remove the `getApiUrl` function entirely if it exists.

**Step 2: Commit**

```bash
git add ui/src/app/login/page.tsx
git commit -m "refactor(ui): update login to use OAuth API route"
```

---

### Task 25: Update auth.config.ts

**Files:**
- Modify: `ui/src/auth.config.ts`

**Step 1: Use API layer for backend calls**

```typescript
import type { NextAuthConfig } from "next-auth";
import Credentials from "next-auth/providers/credentials";
import { exchangeCodeForToken, fetchUserWithToken } from "@/lib/api/auth-api";

// ... keep interfaces ...

export const authConfig: NextAuthConfig = {
  providers: [
    Credentials({
      id: "backend-oauth",
      name: "Backend OAuth",
      credentials: {
        code: { label: "Auth Code", type: "text" },
      },
      async authorize(credentials) {
        const code = credentials?.code as string;
        if (!code) return null;

        try {
          // Exchange code for JWT token
          const tokenResult = await exchangeCodeForToken(code);
          if (!tokenResult) {
            console.error("Token exchange failed");
            return null;
          }

          // Fetch user data
          const userData = await fetchUserWithToken(tokenResult.token);
          if (!userData) {
            console.error("User fetch failed");
            return null;
          }

          return {
            id: userData.email as string,
            email: userData.email as string,
            name: userData.name as string,
            teams: (userData.teams as Array<{ label: string; code: string; types: string[] }>).map((t) => ({
              ...t,
              name: t.code || t.label,
            })),
            roles: userData.roles as string[],
            accessToken: tokenResult.token,
          };
        } catch (error) {
          console.error("Authorization error:", error);
          return null;
        }
      },
    }),
  ],

  // ... keep rest of config ...
};
```

**Step 2: Commit**

```bash
git add ui/src/auth.config.ts
git commit -m "refactor(ui): update auth config to use API layer"
```

---

## Phase 5: Cleanup

### Task 26: Delete old client-side API file

**Files:**
- Delete: `ui/src/lib/api.ts`

**Step 1: Delete file**

```bash
git rm ui/src/lib/api.ts
git commit -m "chore(ui): delete old client-side API file"
```

---

### Task 27: Delete middleware

**Files:**
- Delete: `ui/src/middleware.ts`

**Step 1: Delete file**

```bash
git rm ui/src/middleware.ts
git commit -m "chore(ui): delete deprecated middleware"
```

---

### Task 28: Create startup validation

**Files:**
- Create: `ui/src/instrumentation.ts`

**Step 1: Create file**

```typescript
const REQUIRED_ENV_VARS = ["AUTH_SECRET", "NEXTAUTH_URL", "BACKEND_URL"] as const;

const ENV_ALIASES: Record<string, string> = {
  AUTH_SECRET: "NEXTAUTH_SECRET",
};

function getEnvVar(name: string): string | undefined {
  const value = process.env[name];
  if (value) return value;

  const alias = ENV_ALIASES[name];
  if (alias) {
    const aliasValue = process.env[alias];
    if (aliasValue) {
      console.warn(`  Using deprecated ${alias}, please rename to ${name}`);
      return aliasValue;
    }
  }

  return undefined;
}

export async function register() {
  if (process.env.NEXT_RUNTIME !== "nodejs") {
    return;
  }

  const missing: string[] = [];

  for (const name of REQUIRED_ENV_VARS) {
    if (!getEnvVar(name)) {
      const alias = ENV_ALIASES[name];
      missing.push(alias ? `${name} (or ${alias})` : name);
    }
  }

  if (missing.length > 0) {
    console.error("\n" + "=".repeat(60));
    console.error("Missing required environment variables:");
    console.error("=".repeat(60));
    for (const name of missing) {
      console.error(`   - ${name}`);
    }
    console.error("");
    console.error("Copy .env.example to .env.local and fill in values:");
    console.error("   cp .env.example .env.local");
    console.error("");
    console.error("Generate AUTH_SECRET with:");
    console.error("   openssl rand -base64 32");
    console.error("=".repeat(60) + "\n");
    process.exit(1);
  }
}
```

**Step 2: Commit**

```bash
git add ui/src/instrumentation.ts
git commit -m "feat(ui): add startup validation for required env vars"
```

---

### Task 29: Update .env.example

**Files:**
- Modify: `ui/.env.example`

**Step 1: Replace content**

```bash
# =============================================================================
# Required Environment Variables
# =============================================================================
# All variables below MUST be set. The app will not start without them.

# Internal backend API URL (server-side only, never exposed to browser)
# In Kubernetes: http://api-service.bot-api.svc.cluster.local:8080
BACKEND_URL=http://localhost:8080

# This app's public URL (for OAuth callbacks)
# In production: https://ui.chatbot.com
NEXTAUTH_URL=http://localhost:3000

# Auth secret for JWT encryption
# Generate with: openssl rand -base64 32
AUTH_SECRET=
```

**Step 2: Commit**

```bash
git add ui/.env.example
git commit -m "docs(ui): update .env.example with simplified config"
```

---

### Task 30: Update next.config.ts

**Files:**
- Modify: `ui/next.config.ts`

**Step 1: Simplify config**

```typescript
import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",

  images: {
    remotePatterns: [
      {
        protocol: "https",
        hostname: "**",
      },
    ],
  },

  experimental: {
    serverActions: {
      allowedOrigins: process.env.NEXTAUTH_URL
        ? [new URL(process.env.NEXTAUTH_URL).host]
        : [],
    },
  },

  async headers() {
    return [
      {
        source: "/(.*)",
        headers: [
          {
            key: "Content-Security-Policy",
            value:
              "frame-ancestors 'self' https://*.datadoghq.com https://*.datadoghq.eu https://*.datadoghq.dev",
          },
        ],
      },
    ];
  },
};

export default nextConfig;
```

**Step 2: Commit**

```bash
git add ui/next.config.ts
git commit -m "refactor(ui): simplify next.config.ts"
```

---

## Phase 6: Verification

### Task 31: Run tests and fix any failures

**Step 1: Run existing tests**

```bash
cd ui && npm test
```

**Step 2: Fix any import errors or test failures**

Tests may fail due to:
- Imports of deleted `lib/api.ts`
- Mocking of `/backend/*` paths instead of `/api/*`

Update test mocks as needed.

**Step 3: Commit fixes**

```bash
git add -A
git commit -m "test(ui): fix tests after API refactor"
```

---

### Task 32: Manual verification

**Step 1: Start dev server**

```bash
cd ui && npm run dev
```

**Step 2: Verify startup validation**

Remove `.env.local` temporarily:
```bash
mv .env.local .env.local.bak && npm run dev
```
Expected: Server exits with error listing missing vars.

Restore:
```bash
mv .env.local.bak .env.local
```

**Step 3: Verify browser Network tab**

1. Open app in browser
2. Open DevTools → Network
3. Navigate through the app
4. Verify ALL API calls go to `/api/*` paths
5. Verify NO calls to `/backend/*`

**Step 4: Verify OAuth flow**

1. Go to `/login`
2. Click "Continue with Google"
3. Verify redirect to `/api/oauth/google`
4. Verify OAuth completes successfully

---

## Verification Checklist

- [ ] All files in `lib/api/` have `import "server-only"` at top
- [ ] All files in `lib/server-actions/` have `"use server"` at top
- [ ] No `NEXT_PUBLIC_API_URL` or `NEXT_PUBLIC_BACKEND_URL` in codebase
- [ ] Browser Network tab shows only `/api/*` calls for queries
- [ ] Mutations use server actions (no fetch POST/PATCH/PUT/DELETE in client code)
- [ ] No `/backend/*` paths anywhere
- [ ] `middleware.ts` deleted
- [ ] `lib/api.ts` (old client-side one) deleted
- [ ] `useAuth` does not expose `accessToken`
- [ ] App starts successfully with 3 env vars
- [ ] App fails with clear error when env vars missing
- [ ] OAuth login works through `/api/oauth/*`
- [ ] All tests pass

---

## Backend Configuration Reminder

Ensure Spring Boot API OAuth redirect URIs point to the UI:

```
Google: https://ui.chatbot.com/login
Azure:  https://ui.chatbot.com/login
```

(The OAuth callback redirects back to `/login?code=xxx` which NextAuth handles)
