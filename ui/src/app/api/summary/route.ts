import { backendErrorResponse } from "../_lib/backend-error";
import { backendFetch, unauthorizedResponse } from "../_lib/backend-fetch";

export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);
  const from = searchParams.get("from");
  const to = searchParams.get("to");

  const params = new URLSearchParams();
  if (from) params.append("from", from);
  if (to) params.append("to", to);
  const query = params.toString();

  const response = await backendFetch(request, `/summary${query ? `?${query}` : ""}`, {}, "/summary");
  if (!response) return unauthorizedResponse();

  if (!response.ok) {
    return backendErrorResponse(response);
  }

  const data = await response.json();
  return Response.json(data);
}
