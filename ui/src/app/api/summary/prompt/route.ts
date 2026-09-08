import { NextRequest } from "next/server";
import { backendErrorResponse } from "../../_lib/backend-error";
import { backendFetch, unauthorizedResponse } from "../../_lib/backend-fetch";
import { validateCsrfToken } from "../../_lib/csrf";

export async function GET(request: NextRequest) {
  const csrfError = validateCsrfToken(request);
  if (csrfError) return csrfError;

  const response = await backendFetch(request, "/summary/prompt");
  if (!response) return unauthorizedResponse();

  if (!response.ok) {
    return backendErrorResponse(response);
  }

  const data = await response.json();
  return Response.json(data);
}
