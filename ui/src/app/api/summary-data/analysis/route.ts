import { NextRequest } from "next/server";
import { backendAccessToken, errorResponse, unauthorizedResponse } from "../../_lib/backend-fetch";
import { validateCsrfToken } from "../../_lib/csrf";

const BACKEND_URL = process.env.BACKEND_URL!;

export async function GET(request: NextRequest) {
  const accessToken = await backendAccessToken(request);

  if (!accessToken) {
    return unauthorizedResponse();
  }

  const csrfError = validateCsrfToken(request);
  if (csrfError) return csrfError;

  const backendPath = `/summary-data/analysis`;
  const url = `${BACKEND_URL}${backendPath}`;

  try {
    const response = await fetch(url, {
      headers: {
        Authorization: `Bearer ${accessToken}`,
        Accept: "application/zip",
      },
    });

    if (!response.ok) {
      return errorResponse(`Backend error: ${response.status}`, response.status);
    }

    const blob = await response.blob();

    return new Response(blob, {
      headers: {
        "Content-Type": "application/zip",
        "Content-Disposition": 'attachment; filename="analysis.zip"',
      },
    });
  } catch (error) {
    console.error("Error fetching analysis bundle:", error);
    return errorResponse("Failed to fetch analysis bundle from backend", 502);
  }
}
