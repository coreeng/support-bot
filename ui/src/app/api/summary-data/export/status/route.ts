import { backendFetch, errorResponse, unauthorizedResponse } from "@/app/api/_lib/backend-fetch";
import { NextRequest } from "next/server";

export async function GET(request: NextRequest) {
  const response = await backendFetch(request, "/summary-data/export/status");
  if (!response) return unauthorizedResponse();

  if (!response.ok) {
    return errorResponse(`Backend error: ${response.status}`, response.status);
  }

  const data = await response.json();
  return Response.json(data);
}
