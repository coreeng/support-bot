import type { NextRequest } from "next/server";
import { backendFetch, errorResponse, unauthorizedResponse } from "../../_lib/backend-fetch";

// Follow-up feedback left after a rating. The backend restricts it to leadership and support engineers,
// so a 403 is passed straight through for the UI to show.
export async function GET(request: NextRequest) {
  const searchParams = request.nextUrl.searchParams;
  const params = new URLSearchParams();
  const from = searchParams.get("from");
  const to = searchParams.get("to");
  if (from) params.set("dateFrom", from);
  if (to) params.set("dateTo", to);
  const query = params.toString();

  const response = await backendFetch(request, `/rating/feedback${query ? `?${query}` : ""}`);
  if (!response) return unauthorizedResponse();

  if (!response.ok) {
    return errorResponse(`Backend error: ${response.status}`, response.status);
  }

  return Response.json(await response.json());
}
