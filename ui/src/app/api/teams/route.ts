import { NextRequest } from "next/server";
import { backendFetch, errorResponse, unauthorizedResponse } from "../_lib/backend-fetch";

interface BackendTeam {
  label?: string;
  code?: string;
  types?: string[];
}

function mapTeam(team: BackendTeam) {
  return {
    name: team.code || team.label || "",
    types: team.types,
  };
}

export async function GET(request: NextRequest) {
  const type = request.nextUrl.searchParams.get("type")?.toUpperCase();

  if (type !== "ESCALATION" && type !== "TENANT") {
    return errorResponse("Invalid type parameter", 400);
  }

  const response = await backendFetch(request, `/team?type=${type}`);
  if (!response) return unauthorizedResponse();

  if (!response.ok) {
    return errorResponse(`Backend error: ${response.status}`, response.status);
  }

  const data = await response.json();
  return Response.json(data.map(mapTeam));
}
