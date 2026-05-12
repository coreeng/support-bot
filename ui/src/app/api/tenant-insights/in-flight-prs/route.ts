import { backendFetch, errorResponse, unauthorizedResponse } from "../../_lib/backend-fetch";

export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);
  const team = searchParams.get("team");

  const params = new URLSearchParams();
  if (team) params.append("team", team);
  const query = params.toString();

  const response = await backendFetch(request, `/tenant-insights/in-flight-prs${query ? `?${query}` : ""}`);
  if (!response) return unauthorizedResponse();

  if (!response.ok) {
    return errorResponse(`Backend error: ${response.status}`, response.status);
  }

  const data = await response.json();
  return Response.json(data);
}
