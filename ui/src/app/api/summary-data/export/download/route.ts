import { backendAccessToken, errorResponse, proxyFetch, unauthorizedResponse } from "@/app/api/_lib/backend-fetch";
import { validateCsrfToken } from "@/app/api/_lib/csrf";
import { NextRequest } from "next/server";

const BACKEND_URL = process.env.BACKEND_URL!;

export async function GET(request: NextRequest) {
  const accessToken = await backendAccessToken(request);

  if (!accessToken) {
    return unauthorizedResponse();
  }

  // CSRF-checked despite being a GET: the response exports sensitive data.
  const csrfError = validateCsrfToken(request);
  if (csrfError) return csrfError;

  // proxyFetch (not backendFetch) since backendFetch hardcodes Accept/Content-Type to
  // application/json — wrong for a binary zip download — but this still gets the same
  // logging and network-failure-to-502 handling as the other two export routes.
  const path = "/summary-data/export/download";
  const response = await proxyFetch("proxy", path, `${BACKEND_URL}${path}`, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
      Accept: "application/zip",
    },
  });

  if (!response.ok) {
    return errorResponse(`Backend error: ${response.status}`, response.status);
  }

  const blob = await response.blob();
  // Forward the backend's real filename (it encodes the export's date range) rather than
  // hardcoding one.
  const contentDisposition = response.headers.get("Content-Disposition") ?? 'attachment; filename="threads.zip"';

  return new Response(blob, {
    headers: {
      "Content-Type": "application/zip",
      "Content-Disposition": contentDisposition,
    },
  });
}
