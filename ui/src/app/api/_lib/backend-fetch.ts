import { auth } from "@/auth";

const BACKEND_URL = process.env.BACKEND_URL!;

/**
 * Fetch with logging and error handling.
 * Non-ok responses and network failures are always logged.
 * Set PROXY_LOGGING=true to also log successful requests.
 * Returns a 502 on network failure instead of throwing.
 */
export async function proxyFetch(
  tag: string,
  path: string,
  url: string,
  options: RequestInit
): Promise<Response> {
  const method = options.method?.toUpperCase() || "GET";
  const start = Date.now();

  try {
    const response = await fetch(url, options);
    if (!response.ok) {
      console.error(`[${tag}] ${method} ${path} ${response.status} (${Date.now() - start}ms)`);
    } else if (process.env.PROXY_LOGGING === "true") {
      console.log(`[${tag}] ${method} ${path} ${response.status} (${Date.now() - start}ms)`);
    }
    return response;
  } catch (error) {
    console.error(`[${tag}] ${method} ${path} FAILED (${Date.now() - start}ms)`, error);
    return Response.json({ error: "Backend service unreachable" }, { status: 502 });
  }
}

/**
 * Authenticated fetch to backend API.
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
  return proxyFetch("proxy", path, url, { ...options, headers });
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
