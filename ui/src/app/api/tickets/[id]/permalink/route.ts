import { NextRequest } from "next/server";
import { redirectToBackendPermalink } from "../../../_lib/permalink-redirect";

export async function GET(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return redirectToBackendPermalink(request, `/ticket/${id}/permalink`, `ticket ${id}`);
}
