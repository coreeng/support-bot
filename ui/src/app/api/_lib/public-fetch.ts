/**
 * Unauthenticated fetch to backend API.
 * Used for OAuth endpoints that don't require a session.
 */
const BACKEND_URL = process.env.BACKEND_URL!;

export async function publicFetch(
  path: string,
  options: RequestInit = {}
): Promise<Response> {
  const headers = new Headers(options.headers);
  headers.set("Content-Type", "application/json");
  headers.set("Accept", "application/json");

  const url = path.startsWith("http") ? path : `${BACKEND_URL}${path}`;

  return fetch(url, { ...options, headers });
}

export { BACKEND_URL };
