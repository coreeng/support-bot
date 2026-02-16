import { NextRequest, NextResponse } from "next/server";
import { backendFetch, unauthorizedResponse } from "../../../_lib/backend-fetch";

function errorPage(message: string, status: number) {
  const html = `<!DOCTYPE html>
<html><head><title>Error</title>
<style>
  body { margin: 0; min-height: 100vh; display: flex; align-items: center; justify-content: center; background: #f9fafb; font-family: system-ui, sans-serif; }
  .card { max-width: 28rem; padding: 2rem; text-align: center; }
  h2 { font-size: 1.5rem; font-weight: bold; color: #dc2626; margin-bottom: 1rem; }
  p { color: #4b5563; margin-bottom: 1rem; }
  a { color: #2563eb; text-decoration: none; }
  a:hover { text-decoration: underline; }
</style>
</head><body><div class="card">
  <h2>Something went wrong</h2>
  <p>${message}</p>
  <p style="color:#9ca3af;font-size:0.875rem;">You can close this tab.</p>
</div></body></html>`;
  return new Response(html, {
    status,
    headers: { "Content-Type": "text/html" },
  });
}

export async function GET(
  _request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;

  try {
    const response = await backendFetch(`/escalation/${id}/permalink`);
    if (!response) return unauthorizedResponse();

    if (!response.ok) {
      console.error(`Permalink backend error for escalation ${id}: status=${response.status}`);
      return errorPage("Unable to load Slack link. Please close this tab and try again.", response.status);
    }

    const data = await response.json();
    if (typeof data.permalink !== "string" || !data.permalink.startsWith("https://")) {
      console.error(`Invalid permalink data for escalation ${id}:`, data.permalink);
      return errorPage("Unable to load Slack link. Please close this tab and try again.", 502);
    }

    return NextResponse.redirect(data.permalink);
  } catch (error) {
    console.error(`Permalink route error for escalation ${id}:`, error);
    return errorPage("Unable to load Slack link. Please close this tab and try again.", 502);
  }
}
