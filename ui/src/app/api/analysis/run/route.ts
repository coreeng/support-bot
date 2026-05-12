import { NextRequest } from "next/server";
import { backendFetch, errorResponse, unauthorizedResponse } from "../../_lib/backend-fetch";

export async function POST(request: NextRequest) {
  const searchParams = request.nextUrl.searchParams.toString();
  const query = searchParams ? `?${searchParams}` : "";

  const response = await backendFetch(request, `/analysis/run${query}`, {
    method: "POST",
  });
  if (!response) return unauthorizedResponse();

  if (!response.ok) {
    return errorResponse(`Backend error: ${response.status}`, response.status);
  }

  return new Response(null, { status: response.status });
}
