import { NextRequest } from "next/server";
import {
  backendFetch,
  unauthorizedResponse,
  errorResponse,
} from "../../_lib/backend-fetch";

interface BackendTeam {
  label?: string;
  code?: string;
  types?: string[];
}

function mapTicket(ticket: Record<string, unknown>) {
  const team = ticket.team as BackendTeam | null;
  const escalations = ticket.escalations as
    | Array<{ team?: BackendTeam; [key: string]: unknown }>
    | undefined;

  return {
    ...ticket,
    id: String(ticket.id),
    team: team ? { name: team.code || team.label || "" } : null,
    escalations:
      escalations?.map((esc) => ({
        ...esc,
        id: String(esc.id),
        team: esc.team ? { name: esc.team.code || esc.team.label || "" } : null,
      })) ?? [],
  };
}

export async function GET(
  _request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;

  const response = await backendFetch(`/ticket/${id}`);
  if (!response) return unauthorizedResponse();

  if (!response.ok) {
    return errorResponse(`Backend error: ${response.status}`, response.status);
  }

  const data = await response.json();
  return Response.json(mapTicket(data));
}

export async function PATCH(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  const body = await request.json();

  const response = await backendFetch(`/ticket/${id}`, {
    method: "PATCH",
    body: JSON.stringify(body),
  });
  if (!response) return unauthorizedResponse();

  if (!response.ok) {
    return errorResponse(`Backend error: ${response.status}`, response.status);
  }

  const data = await response.json();
  return Response.json(mapTicket(data));
}
