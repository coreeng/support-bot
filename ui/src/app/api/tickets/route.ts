import { NextRequest } from "next/server";
import {
  backendFetch,
  unauthorizedResponse,
  errorResponse,
} from "../_lib/backend-fetch";
import { mapTicket } from "./_lib/map-ticket";

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
