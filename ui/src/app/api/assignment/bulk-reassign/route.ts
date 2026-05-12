import { NextRequest } from "next/server";
import { backendFetch, errorResponse, unauthorizedResponse } from "../../_lib/backend-fetch";

export async function POST(request: NextRequest) {
  const body = await request.json();

  const response = await backendFetch(request, "/assignment/bulk-reassign", {
    method: "POST",
    body: JSON.stringify(body),
  });
  if (!response) return unauthorizedResponse();

  if (!response.ok) {
    return errorResponse(`Backend error: ${response.status}`, response.status);
  }

  const data = await response.json();
  return Response.json(data);
}
