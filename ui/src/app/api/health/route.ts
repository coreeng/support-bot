/**
 * Health check endpoint for Kubernetes liveness/readiness probes.
 * This route is excluded from auth in proxy.ts.
 */
export async function GET() {
  return Response.json({ status: "ok" });
}
