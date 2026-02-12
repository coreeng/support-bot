import { NextRequest } from "next/server";
import {
  backendFetch,
  unauthorizedResponse,
  errorResponse,
} from "../../_lib/backend-fetch";

export async function POST(request: NextRequest) {
  const body = await request.json();

  const response = await backendFetch("/assignment/bulk-reassign", {
    method: "POST",
    body: JSON.stringify(body),
  });
  if (!response) return unauthorizedResponse();

  if (!response.ok) {
    return errorResponse(`Backend error: ${response.status}`, response.status);
  }

  const data = await response.json();
  return Response.json(data);
}
