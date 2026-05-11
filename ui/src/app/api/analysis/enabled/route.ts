import { NextRequest } from "next/server";
import { backendFetch, errorResponse, unauthorizedResponse } from "../../_lib/backend-fetch";

export async function GET(request: NextRequest) {
  const response = await backendFetch(request, "/analysis/enabled");
  if (!response) return unauthorizedResponse();

  if (!response.ok) {
    return errorResponse(`Backend error: ${response.status}`, response.status);
  }

  const data = await response.json();
  return Response.json(data);
}
