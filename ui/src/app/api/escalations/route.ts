import { NextRequest } from "next/server";
import {
  backendFetch,
  unauthorizedResponse,
  errorResponse,
} from "../_lib/backend-fetch";

interface BackendTeam {
  label?: string;
  code?: string;
}

export async function GET(request: NextRequest) {
  const searchParams = request.nextUrl.searchParams;
  const page = searchParams.get("page") ?? "0";
  const pageSize = searchParams.get("pageSize") ?? "50";

  const response = await backendFetch(
    `/escalation?page=${page}&pageSize=${pageSize}&escalated=true`
  );
  if (!response) return unauthorizedResponse();

  if (!response.ok) {
    return errorResponse(`Backend error: ${response.status}`, response.status);
  }

  const data = await response.json();

  // Map the escalations to normalize IDs and team names
  const content = data.content.map((e: Record<string, unknown>) => {
    const team = e.team as BackendTeam | null;
    const id = typeof e.id === "object" ? (e.id as { id: unknown }).id : e.id;
    const ticketId =
      typeof e.ticketId === "object"
        ? (e.ticketId as { id: unknown }).id
        : e.ticketId;

    return {
      id: String(id),
      ticketId: String(ticketId),
      hasThread: !!e.hasThread,
      openedAt: e.openedAt,
      resolvedAt: e.resolvedAt,
      escalatingTeam: e.escalatingTeam,
      team: team ? { name: team.code || team.label || "" } : null,
      tags: e.tags ?? [],
      impact: e.impact ?? null,
    };
  });

  return Response.json({
    page: data.page,
    totalPages: data.totalPages,
    totalElements: data.totalElements,
    content,
  });
}
