import { NextResponse } from "next/server";
import { auth } from "@/auth";
import { readFile } from "fs/promises";
import { join } from "path";

export async function GET() {
  const session = await auth();

  // Check authentication
  if (!session?.user) {
    return new NextResponse("Unauthorized", { status: 401 });
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

