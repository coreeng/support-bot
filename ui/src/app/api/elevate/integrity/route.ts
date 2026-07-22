import { INTEGRITY_PARAMS, proxyElevateGet } from "@/app/api/elevate/_lib/proxy-elevate-get";
import type { NextRequest } from "next/server";

export async function GET(request: NextRequest) {
  return proxyElevateGet(request, "/elevate/integrity", INTEGRITY_PARAMS);
}
