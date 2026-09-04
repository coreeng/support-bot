import { NextRequest } from "next/server";
import { backendErrorResponse } from "../_lib/backend-error";
import { backendFetch, unauthorizedResponse } from "../_lib/backend-fetch";
import { validateCsrfToken } from "../_lib/csrf";

export async function GET(request: NextRequest) {
  // An uncached window triggers classification and paid summary generation on the backend,
  // so this GET is guarded like a mutation.
  const csrfError = validateCsrfToken(request);
  if (csrfError) return csrfError;

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
