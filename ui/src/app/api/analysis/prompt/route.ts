import { NextRequest } from "next/server";
import { backendFetch, errorResponse, unauthorizedResponse } from "../../_lib/backend-fetch";
import { validateCsrfToken } from "../../_lib/csrf";

export async function GET(request: NextRequest) {
  const csrfError = validateCsrfToken(request);
  if (csrfError) return csrfError;

  const response = await backendFetch(request, "/analysis/prompt");
  if (!response) return unauthorizedResponse();

  if (!response.ok) {
    return errorResponse(`Backend error: ${response.status}`, response.status);
  }

  const data = await response.json();
  return Response.json(data);
}
