/**
 * Error response for a failed backend call, preserving the backend's ProblemDetail `code` so the
 * client can tell failures apart instead of collapsing them all onto the status.
 *
 * Kept out of backend-fetch.ts so route tests can exercise it for real: that module pulls in
 * next-auth, which the Jest transform cannot load.
 */
export async function backendErrorResponse(response: Response) {
  let code: string | undefined;
  try {
    code = ((await response.json()) as { code?: string })?.code;
  } catch {
    // Proxies and gateways can answer with a non-JSON body; the status alone still reaches the client.
  }
  return Response.json({ error: `Backend error: ${response.status}`, code }, { status: response.status });
}
