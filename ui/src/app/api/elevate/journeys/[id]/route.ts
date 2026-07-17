import { proxyElevateGet, SNAPSHOT_PARAMS } from "@/app/api/elevate/_lib/proxy-elevate-get";
import type { NextRequest } from "next/server";

export async function GET(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return proxyElevateGet(request, `/elevate/journeys/${encodeURIComponent(id)}`, SNAPSHOT_PARAMS, "/elevate/journeys/:id");
}
