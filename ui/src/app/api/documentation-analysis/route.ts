import { NextRequest } from "next/server";
import { backendErrorResponse } from "../_lib/backend-error";
import { backendFetch, unauthorizedResponse } from "../_lib/backend-fetch";
import { validateCsrfToken } from "../_lib/csrf";

export async function GET(request: NextRequest) {
  // The backend may start classifying the window with the LLM, so this GET is guarded like a mutation.
  const csrfError = validateCsrfToken(request);
  if (csrfError) return csrfError;

  const { searchParams } = new URL(request.url);
  const params = new URLSearchParams();
  for (const name of ["from", "to"]) {
    const value = searchParams.get(name);
    if (value) params.append(name, value);
  }
  const query = params.toString();

  const response = await backendFetch(request, `/documentation-analysis${query ? `?${query}` : ""}`, {}, "/documentation-analysis");
  if (!response) return unauthorizedResponse();

  if (!response.ok) {
    // The invalid-window problem's detail is written for people, so the page shows it.
    return backendErrorResponse(response, { forwardDetail: true });
  }

  return Response.json(await response.json());
}
