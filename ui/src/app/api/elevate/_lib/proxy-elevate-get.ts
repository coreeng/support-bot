import { backendFetch, unauthorizedResponse } from "@/app/api/_lib/backend-fetch";
import type { NextRequest } from "next/server";

export const SNAPSHOT_PARAMS = ["snapshotVersion"] as const;
export const PAGE_PARAMS = ["snapshotVersion", "page", "pageSize", "query", "relationship", "sort", "direction"] as const;
export const INTEGRITY_PARAMS = ["snapshotVersion", "page", "pageSize", "query", "type", "sort", "direction"] as const;

function forwardResponse(response: Response) {
  const headers = new Headers();
  const contentType = response.headers.get("content-type");
  if (contentType) headers.set("content-type", contentType);
  return new Response(response.body, { status: response.status, headers });
}

export async function proxyElevateGet(request: NextRequest, path: string, allowedParams: readonly string[] = []) {
  const params = new URLSearchParams();
  for (const key of allowedParams) {
    const value = request.nextUrl.searchParams.get(key);
    if (value !== null && value !== "") params.set(key, value);
  }

  const query = params.toString();
  const response = await backendFetch(request, `${path}${query ? `?${query}` : ""}`, { signal: request.signal });
  if (!response) return unauthorizedResponse();
  return forwardResponse(response);
}
