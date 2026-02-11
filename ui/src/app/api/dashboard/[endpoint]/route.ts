import { NextRequest } from "next/server";
import {
  backendFetch,
  unauthorizedResponse,
  errorResponse,
} from "../../_lib/backend-fetch";

// All valid dashboard endpoints that map to /dashboard/* on the backend
const VALID_ENDPOINTS = new Set([
  "first-response-distribution",
  "first-response-percentiles",
  "unattended-queries-count",
  "resolution-percentiles",
  "resolution-duration-distribution",
  "resolution-times-by-week",
  "unresolved-ticket-ages",
  "incoming-vs-resolved-rate",
  "avg-escalation-duration-by-tag",
  "escalation-percentage-by-tag",
  "escalation-trends-by-date",
  "escalations-by-team",
  "escalations-by-impact",
  "weekly-ticket-counts",
  "weekly-comparison",
  "top-escalated-tags-this-week",
  "resolution-time-by-tag",
]);

export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ endpoint: string }> }
) {
  const { endpoint } = await params;

  if (!VALID_ENDPOINTS.has(endpoint)) {
    return errorResponse("Unknown endpoint", 404);
  }

  const searchParams = request.nextUrl.searchParams;
  const dateFrom = searchParams.get("dateFrom");
  const dateTo = searchParams.get("dateTo");

  // Build query params for backend
  const backendParams = new URLSearchParams();
  if (dateFrom) backendParams.append("dateFrom", dateFrom);
  if (dateTo) backendParams.append("dateTo", dateTo);
  const queryString = backendParams.toString();
  const backendPath = `/dashboard/${endpoint}${queryString ? `?${queryString}` : ""}`;

  const response = await backendFetch(backendPath);
  if (!response) return unauthorizedResponse();

  if (!response.ok) {
    return errorResponse(`Backend error: ${response.status}`, response.status);
  }

  const data = await response.json();
  return Response.json(data);
}
