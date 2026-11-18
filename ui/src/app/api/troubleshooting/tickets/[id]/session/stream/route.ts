import { NextRequest } from "next/server";
import { backendAccessToken, errorResponse, unauthorizedResponse } from "../../../../../_lib/backend-fetch";
import { badRequest, parseId } from "../../../../_lib/params";

export const dynamic = "force-dynamic";

// backendFetch forces `Accept: application/json`, which the event-stream endpoint would answer with 406.
export async function GET(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  const ticketId = parseId((await params).id);
  if (ticketId === null) return badRequest("Invalid ticket id");
  const afterParam = request.nextUrl.searchParams.get("after") ?? "0";
  const after = parseId(afterParam);
  if (after === null) return badRequest("Invalid after");
  const lastEventId = parseId(request.headers.get("Last-Event-ID") ?? "");

  const token = await backendAccessToken(request);
  if (!token) return unauthorizedResponse();

  const headers = new Headers({ Accept: "text/event-stream", Authorization: `Bearer ${token}` });
  if (lastEventId !== null) headers.set("Last-Event-ID", String(lastEventId));

  let upstream: Response;
  try {
    upstream = await fetch(`${process.env.BACKEND_URL}/troubleshooting/ticket/${ticketId}/session/stream?after=${after}`, {
      headers,
      signal: request.signal,
      cache: "no-store",
    });
  } catch {
    return errorResponse("Backend service unreachable", 502);
  }
  if (!upstream.ok || !upstream.body) return errorResponse(`Backend error: ${upstream.status}`, upstream.status);

  return new Response(upstream.body, {
    headers: {
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache, no-transform",
      "X-Accel-Buffering": "no",
    },
  });
}
