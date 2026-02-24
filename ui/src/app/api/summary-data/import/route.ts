import { NextRequest } from "next/server";
import { auth } from "@/auth";
import { unauthorizedResponse, errorResponse } from "../../_lib/backend-fetch";

const BACKEND_URL = process.env.BACKEND_URL!;

export async function POST(request: NextRequest) {
  const session = await auth();

  if (!session?.accessToken) {
    return unauthorizedResponse();
  }

  // Validate CSRF token
  const csrfTokenFromHeader = request.headers.get("X-CSRF-Token");
  const csrfCookieName = process.env.NODE_ENV === "production"
    ? "__Host-authjs.csrf-token"
    : "authjs.csrf-token";
  const csrfCookieValue = request.cookies.get(csrfCookieName)?.value;

  if (!csrfTokenFromHeader || !csrfCookieValue) {
    return errorResponse("Missing CSRF token", 403);
  }

  // NextAuth CSRF token format: "token|hash"
  // The cookie contains "token|hash", the header should contain just "token"
  // We need to extract the token part from the cookie and compare
  const cookieToken = csrfCookieValue.split("|")[0];

  if (csrfTokenFromHeader !== cookieToken) {
    return errorResponse("Invalid CSRF token", 403);
  }

  try {
    // Get the form data from the request
    const formData = await request.formData();

    // Forward the form data to the backend
    const backendPath = `/summary-data/import`;
    const url = `${BACKEND_URL}${backendPath}`;

    const response = await fetch(url, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${session.accessToken}`,
      },
      body: formData, // Send the FormData directly
    });

    if (!response.ok) {
      return errorResponse(`Backend error: ${response.status}`, response.status);
    }

    const data = await response.json();
    return Response.json(data);
  } catch (error) {
    console.error("Error uploading file:", error);
    return errorResponse("Failed to upload file", 500);
  }
}

