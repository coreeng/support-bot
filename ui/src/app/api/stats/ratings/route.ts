import { NextRequest } from "next/server";
import {
  backendFetch,
  unauthorizedResponse,
  errorResponse,
} from "../../_lib/backend-fetch";

interface RatingsResult {
  average: number | null;
  count: number | null;
  weekly?: Array<{ weekStart: string; average: number | null; count: number | null }>;
}

export async function GET(request: NextRequest) {
  const searchParams = request.nextUrl.searchParams;
  const from = searchParams.get("from");
  const to = searchParams.get("to");

  // Build request body for stats endpoint
  const statsRequest: Record<string, unknown> = { type: "ticket-ratings" };
  if (from) statsRequest.from = from;
  if (to) statsRequest.to = to;

  const response = await backendFetch("/stats", {
    method: "POST",
    body: JSON.stringify([statsRequest]),
  });
  if (!response) return unauthorizedResponse();

  if (!response.ok) {
    return errorResponse(`Backend error: ${response.status}`, response.status);
  }

  const data = await response.json();
  const first = data?.[0];

  // Normalize the response format
  if (first?.values) {
    const values = first.values as RatingsResult;
    const rootWeekly = first.weekly;
    return Response.json({
      average: values.average ?? null,
      count: values.count ?? null,
      weekly: Array.isArray(values.weekly)
        ? values.weekly
        : Array.isArray(rootWeekly)
          ? rootWeekly
          : undefined,
    });
  }

  return Response.json({ average: null, count: null });
}
