import { auth } from "@/auth";

const BACKEND_URL = process.env.BACKEND_URL!;

/**
 * Authenticated fetch to backend API.
 * Automatically injects the user's access token from the session.
 * Returns null if user is not authenticated (caller should handle 401).
 */
export async function backendFetch(
  path: string,
  options: RequestInit = {}
): Promise<Response | null> {
  const session = await auth();

  if (!session?.accessToken) {
    return null;
  }

  const headers = new Headers(options.headers);
  headers.set("Content-Type", "application/json");
  headers.set("Accept", "application/json");
  headers.set("Authorization", `Bearer ${session.accessToken}`);

  const url = path.startsWith("http") ? path : `${BACKEND_URL}${path}`;

  return fetch(url, { ...options, headers });
}

/**
 * Helper to create a 401 response for unauthenticated requests.
 */
export function unauthorizedResponse() {
  return Response.json({ error: "Unauthorized" }, { status: 401 });
}

/**
 * Helper to create an error response.
 */
export function errorResponse(message: string, status = 500) {
  return Response.json({ error: message }, { status });
}
