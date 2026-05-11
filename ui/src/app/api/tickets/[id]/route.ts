import { NextRequest } from "next/server";
import { backendFetch, errorResponse, unauthorizedResponse } from "../../_lib/backend-fetch";
import { mapTicket } from "../_lib/map-ticket";

export async function GET(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;

  const response = await backendFetch(request, `/ticket/${id}`);
  if (!response) return unauthorizedResponse();

  if (!response.ok) {
    return errorResponse(`Backend error: ${response.status}`, response.status);
  }

  const data = await response.json();
  return Response.json(mapTicket(data));
}

export async function PATCH(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const body = await request.json();

  const response = await backendFetch(request, `/ticket/${id}`, {
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
