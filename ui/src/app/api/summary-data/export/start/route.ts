import { backendFetch, errorResponse, unauthorizedResponse } from "@/app/api/_lib/backend-fetch";
import { NextRequest } from "next/server";

export async function POST(request: NextRequest) {
  const searchParams = request.nextUrl.searchParams.toString();
  const query = searchParams ? `?${searchParams}` : "";

  const response = await backendFetch(request, `/summary-data/export/start${query}`, {
    method: "POST",
  });
  if (!response) return unauthorizedResponse();

  if (!response.ok) {
    return errorResponse(`Backend error: ${response.status}`, response.status);
  }

  return new Response(null, { status: response.status });
}
