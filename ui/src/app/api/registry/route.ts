import {
  backendFetch,
  unauthorizedResponse,
  errorResponse,
} from "../_lib/backend-fetch";

export async function GET() {
  // Fetch both impacts and tags in parallel
  const [impactsRes, tagsRes] = await Promise.all([
    backendFetch("/registry/impact"),
    backendFetch("/registry/tag"),
  ]);

  if (!impactsRes || !tagsRes) return unauthorizedResponse();

  if (!impactsRes.ok || !tagsRes.ok) {
    return errorResponse("Failed to fetch registry", 500);
  }

  const [impacts, tags] = await Promise.all([
    impactsRes.json(),
    tagsRes.json(),
  ]);

  return Response.json({ impacts, tags });
}
