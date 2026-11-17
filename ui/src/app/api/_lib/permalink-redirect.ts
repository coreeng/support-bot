import { NextRequest, NextResponse } from "next/server";
import { backendFetch, unauthorizedResponse } from "./backend-fetch";

const ERROR_MESSAGE = "Unable to load Slack link. Please close this tab and try again.";

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

/**
 * Fetches `{ permalink }` from a backend endpoint and redirects the browser to it. Links are opened in a new
 * tab, so failures render a small standalone error page rather than JSON.
 *
 * @param label identifies the resource in logs, e.g. `escalation 42`
 */
export async function redirectToBackendPermalink(request: NextRequest, backendPath: string, label: string) {
  try {
    const response = await backendFetch(request, backendPath);
    if (!response) return unauthorizedResponse();

    if (!response.ok) {
      console.error(`Permalink backend error for ${label}: status=${response.status}`);
      return errorPage(ERROR_MESSAGE, response.status);
    }

    const data = await response.json();
    if (typeof data.permalink !== "string" || !data.permalink.startsWith("https://")) {
      console.error(`Invalid permalink data for ${label}:`, data.permalink);
      return errorPage(ERROR_MESSAGE, 502);
    }

    return NextResponse.redirect(data.permalink);
  } catch (error) {
    console.error(`Permalink route error for ${label}:`, error);
    return errorPage(ERROR_MESSAGE, 502);
  }
}
