import { backendFetch, errorResponse, unauthorizedResponse } from "../../_lib/backend-fetch";

export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);
  const dateFrom = searchParams.get("dateFrom");
  const dateTo = searchParams.get("dateTo");

  const params = new URLSearchParams();
  if (dateFrom) params.append("dateFrom", dateFrom);
  if (dateTo) params.append("dateTo", dateTo);
  const query = params.toString();

  const response = await backendFetch(request, `/tenant-insights/pr-stats${query ? `?${query}` : ""}`);
  if (!response) return unauthorizedResponse();

  if (!response.ok) {
    return errorResponse(`Backend error: ${response.status}`, response.status);
  }

  const data = await response.json();
  return Response.json(data);
}
