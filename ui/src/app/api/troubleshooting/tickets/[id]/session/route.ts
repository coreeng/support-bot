import { NextRequest } from "next/server";
import { backendErrorResponse } from "../../../../_lib/backend-error";
import { backendFetch, unauthorizedResponse } from "../../../../_lib/backend-fetch";
import { validateCsrfToken } from "../../../../_lib/csrf";
import { badRequest, parseId } from "../../../_lib/params";

type Params = { params: Promise<{ id: string }> };

export async function GET(request: NextRequest, { params }: Params) {
  const ticketId = parseId((await params).id);
  if (ticketId === null) return badRequest("Invalid ticket id");

  const path = `/troubleshooting/ticket/${ticketId}/session`;
  const response = await backendFetch(request, path, {}, "/troubleshooting/ticket/{id}/session");
  if (!response) return unauthorizedResponse();
  if (!response.ok) return backendErrorResponse(response);
  return Response.json(await response.json());
}

export async function POST(request: NextRequest, { params }: Params) {
  const csrfError = validateCsrfToken(request);
  if (csrfError) return csrfError;

  const ticketId = parseId((await params).id);
  if (ticketId === null) return badRequest("Invalid ticket id");

  const path = `/troubleshooting/ticket/${ticketId}/session`;
  const response = await backendFetch(request, path, { method: "POST" }, "/troubleshooting/ticket/{id}/session");
  if (!response) return unauthorizedResponse();
  if (!response.ok) return backendErrorResponse(response);
  return Response.json(await response.json());
}
