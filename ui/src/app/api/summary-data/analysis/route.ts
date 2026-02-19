import { NextRequest } from "next/server";
import { auth } from "@/auth";
import { unauthorizedResponse, errorResponse } from "../../_lib/backend-fetch";

const BACKEND_URL = process.env.BACKEND_URL!;

export async function GET(request: NextRequest) {
  const session = await auth();

  if (!session?.accessToken) {
    return unauthorizedResponse();
  }

  // Validate CSRF token for GET requests that export sensitive data
  const csrfTokenFromHeader = request.headers.get("X-CSRF-Token");
  const csrfCookieName = process.env.NODE_ENV === "production"
    ? "__Host-authjs.csrf-token"
    : "authjs.csrf-token";
  const csrfCookieValue = request.cookies.get(csrfCookieName)?.value;

  if (!csrfTokenFromHeader || !csrfCookieValue) {
    return errorResponse("Missing CSRF token", 403);
  }

  // NextAuth CSRF token format: "token|hash"
  const cookieToken = csrfCookieValue.split("|")[0];

  if (csrfTokenFromHeader !== cookieToken) {
    return errorResponse("Invalid CSRF token", 403);
  }

  const searchParams = request.nextUrl.searchParams;

  const backendPath = `/summary-data/analysis`;
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
      "Content-Disposition": 'attachment; filename="analysis.zip"',
    },
  });
}

