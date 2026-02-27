/**
 * Unauthenticated fetch to backend API.
 * Used for OAuth endpoints that don't require a session.
 */
import { proxyFetch } from "./backend-fetch";

const BACKEND_URL = process.env.BACKEND_URL!;

export async function publicFetch(
  path: string,
  options: RequestInit = {}
): Promise<Response> {
  const headers = new Headers(options.headers);
  headers.set("Content-Type", "application/json");
  headers.set("Accept", "application/json");

  const url = path.startsWith("http") ? path : `${BACKEND_URL}${path}`;
  return proxyFetch("proxy:public", path, url, { ...options, headers });
}

export { BACKEND_URL };
