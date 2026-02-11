import { NextRequest } from "next/server";
import {
  backendFetch,
  unauthorizedResponse,
  errorResponse,
} from "../_lib/backend-fetch";

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

export async function GET(request: NextRequest) {
  const searchParams = request.nextUrl.searchParams;
  const page = searchParams.get("page") ?? "0";
  const pageSize = searchParams.get("pageSize") ?? "50";
  const dateFrom = searchParams.get("dateFrom");
  const dateTo = searchParams.get("dateTo");

  const params = new URLSearchParams({ page, pageSize });
  if (dateFrom) params.append("dateFrom", dateFrom);
  if (dateTo) params.append("dateTo", dateTo);

  const response = await backendFetch(`/ticket?${params}`);
  if (!response) return unauthorizedResponse();

  if (!response.ok) {
    return errorResponse(`Backend error: ${response.status}`, response.status);
  }

  const data = await response.json();
  return Response.json({
    ...data,
    content: data.content?.map(mapTicket) ?? [],
  });
}
