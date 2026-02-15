import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/auth";
import { readFile } from "fs/promises";
import { join } from "path";

export async function GET(request: NextRequest) {
  const session = await auth();

  // Check authentication
  if (!session?.user) {
    return new NextResponse("Unauthorized", { status: 401 });
  }

  // Validate CSRF token for GET requests that export sensitive data
  const csrfTokenFromHeader = request.headers.get("X-CSRF-Token");
  const csrfCookieName = process.env.NODE_ENV === "production"
    ? "__Host-authjs.csrf-token"
    : "authjs.csrf-token";
  const csrfCookieValue = request.cookies.get(csrfCookieName)?.value;

  if (!csrfTokenFromHeader || !csrfCookieValue) {
    return new NextResponse("Missing CSRF token", { status: 403 });
  }

  // NextAuth CSRF token format: "token|hash"
  const cookieToken = csrfCookieValue.split("|")[0];

  if (csrfTokenFromHeader !== cookieToken) {
    return new NextResponse("Invalid CSRF token", { status: 403 });
  }

  // Check roles - must be LEADERSHIP or SUPPORT_ENGINEER
  const hasRequiredRole =
    session.user.roles?.includes("LEADERSHIP") ||
    session.user.roles?.includes("SUPPORT_ENGINEER");

  if (!hasRequiredRole) {
    return new NextResponse("Forbidden - requires LEADERSHIP or SUPPORT_ENGINEER role", {
      status: 403,
    });
  }

  try {
    // Read the prompt file from the protected data directory
    const filePath = join(process.cwd(), "src", "data", "gap_analysis_taxonomy_summary-prompt.md");
    const fileContent = await readFile(filePath, "utf-8");

    // Return the file with appropriate headers
    return new NextResponse(fileContent, {
      status: 200,
      headers: {
        "Content-Type": "text/markdown; charset=utf-8",
        "Content-Disposition": 'attachment; filename="gap_analysis_taxonomy_summary-prompt.md"',
      },
    });
  } catch (error) {
    console.error("Error reading prompt file:", error);
    return new NextResponse("File not found", { status: 404 });
  }
}

