import { NextRequest } from "next/server";
import { auth } from "@/auth";
import { unauthorizedResponse, errorResponse } from "../../_lib/backend-fetch";

const BACKEND_URL = process.env.BACKEND_URL!;

export async function GET(request: NextRequest) {
  const session = await auth();

  if (!session?.accessToken) {
    return unauthorizedResponse();
  }

  const searchParams = request.nextUrl.searchParams;
  const days = searchParams.get("days") || "31";

  const backendPath = `/summary-data/export?days=${days}`;
  const url = `${BACKEND_URL}${backendPath}`;

  // Custom fetch for binary data (zip file)
  const response = await fetch(url, {
    headers: {
      Authorization: `Bearer ${session.accessToken}`,
      Accept: "application/zip",
    },
  });

  if (!response.ok) {
    return errorResponse(`Backend error: ${response.status}`, response.status);
  }

  // Stream the zip file directly to the client
  const blob = await response.blob();

  return new Response(blob, {
    headers: {
      "Content-Type": "application/zip",
      "Content-Disposition": 'attachment; filename="content.zip"',
    },
  });
}

