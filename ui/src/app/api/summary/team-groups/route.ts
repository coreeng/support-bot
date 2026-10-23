import { NextRequest } from "next/server";
import { backendErrorResponse } from "../../_lib/backend-error";
import { backendFetch, unauthorizedResponse } from "../../_lib/backend-fetch";

/** The team groups the Support Summary can be narrowed to. Read-only, so no CSRF check. */
export async function GET(request: NextRequest) {
  const response = await backendFetch(request, "/summary/team-groups");
  if (!response) return unauthorizedResponse();

  if (!response.ok) {
    return backendErrorResponse(response);
  }

  const data = await response.json();
  return Response.json(data);
}
