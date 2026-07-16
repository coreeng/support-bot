import { PAGE_PARAMS, proxyElevateGet } from "@/app/api/elevate/_lib/proxy-elevate-get";
import type { NextRequest } from "next/server";

export async function GET(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return proxyElevateGet(request, `/elevate/products/${encodeURIComponent(id)}/users`, PAGE_PARAMS);
}
