import { NextRequest } from "next/server";
import { backendErrorResponse } from "../../../../../../../_lib/backend-error";
import { backendFetch, unauthorizedResponse } from "../../../../../../../_lib/backend-fetch";
import { validateCsrfToken } from "../../../../../../../_lib/csrf";
import { badRequest, parseId } from "../../../../../../_lib/params";

type DecisionBody = { decision?: unknown; text?: unknown };

export async function POST(request: NextRequest, { params }: { params: Promise<{ id: string; seq: string }> }) {
  const csrfError = validateCsrfToken(request);
  if (csrfError) return csrfError;

  const { id, seq } = await params;
  const ticketId = parseId(id);
  const proposalSeq = parseId(seq);
  if (ticketId === null || proposalSeq === null) return badRequest("Invalid ticket id or proposal");

  const body = (await request.json().catch(() => null)) as DecisionBody | null;
  if (body?.decision !== "APPROVED" && body?.decision !== "REJECTED") return badRequest("Decision must be APPROVED or REJECTED");
  const text = typeof body.text === "string" ? body.text : null;

  const response = await backendFetch(
    request,
    `/troubleshooting/ticket/${ticketId}/session/proposals/${proposalSeq}/decision`,
    { method: "POST", body: JSON.stringify({ decision: body.decision, text }) },
    "/troubleshooting/ticket/{id}/session/proposals/{seq}/decision"
  );
  if (!response) return unauthorizedResponse();
  if (!response.ok) return backendErrorResponse(response);
  return Response.json(await response.json());
}
