import { NextRequest } from "next/server";
import { backendErrorResponse } from "../../../../../_lib/backend-error";
import { backendFetch, unauthorizedResponse } from "../../../../../_lib/backend-fetch";
import { validateCsrfToken } from "../../../../../_lib/csrf";
import { badRequest, parseId } from "../../../../_lib/params";

export async function POST(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  const csrfError = validateCsrfToken(request);
  if (csrfError) return csrfError;

  const ticketId = parseId((await params).id);
  if (ticketId === null) return badRequest("Invalid ticket id");

  const body = (await request.json().catch(() => null)) as { text?: unknown } | null;
  if (typeof body?.text !== "string" || body.text.trim() === "") return badRequest("Message text is required");

  const response = await backendFetch(
    request,
    `/troubleshooting/ticket/${ticketId}/session/messages`,
    { method: "POST", body: JSON.stringify({ text: body.text }) },
    "/troubleshooting/ticket/{id}/session/messages"
  );
  if (!response) return unauthorizedResponse();
  if (!response.ok) return backendErrorResponse(response);
  return Response.json(await response.json(), { status: response.status });
}
