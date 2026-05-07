import { NextRequest } from "next/server";
import {
  backendFetch,
  unauthorizedResponse,
  errorResponse,
} from "../../_lib/backend-fetch";

export async function GET(request: NextRequest) {
  const response = await backendFetch(request, "/tenant-insights/enabled");
  if (!response) return unauthorizedResponse();

  if (response.status === 404) {
    return Response.json({ enabled: false });
  }

  if (!response.ok) {
    return errorResponse(`Backend error: ${response.status}`, response.status);
  }

  const data = await response.json();
  return Response.json(data);
}
