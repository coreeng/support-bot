import type { NextRequest } from "next/server";
import { backendFetch } from "../_lib/backend-fetch";
import { GET } from "./route";

jest.mock("../_lib/backend-fetch", () => ({
  backendFetch: jest.fn(),
  errorResponse: (message: string, status = 500) => ({ status, json: async () => ({ error: message }) }),
  unauthorizedResponse: () => ({ status: 401, json: async () => ({ error: "Unauthorized" }) }),
}));

const mockBackendFetch = backendFetch as jest.MockedFunction<typeof backendFetch>;

const originalResponse = global.Response;
beforeAll(() => {
  global.Response = {
    json: (data: unknown, init?: { status?: number }) => ({ status: init?.status ?? 200, json: async () => data }),
  } as unknown as typeof Response;
});
afterAll(() => {
  global.Response = originalResponse;
});

function makeRequest({ query = "", csrfHeader = "csrf-token" }: { query?: string; csrfHeader?: string | null } = {}): NextRequest {
  return {
    url: `http://localhost/api/documentation-analysis${query}`,
    headers: { get: (name: string) => (name === "X-CSRF-Token" ? csrfHeader : null) },
    cookies: { get: (name: string) => (name === "authjs.csrf-token" ? { value: "csrf-token|hash" } : undefined) },
  } as unknown as NextRequest;
}

describe("documentation analysis BFF route", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("forwards only the window to the backend and returns its payload", async () => {
    mockBackendFetch.mockResolvedValue({ ok: true, json: async () => ({ counts: { total: 3 } }) } as Response);
    const request = makeRequest({ query: "?from=2026-03-10&to=2026-03-23&other=x" });

    const response = await GET(request);

    expect(mockBackendFetch).toHaveBeenCalledWith(
      request,
      "/documentation-analysis?from=2026-03-10&to=2026-03-23",
      {},
      "/documentation-analysis"
    );
    await expect(response.json()).resolves.toEqual({ counts: { total: 3 } });
  });

  // The request can start LLM work on the backend, so a cross-site GET must not reach it.
  it("refuses a request without the CSRF token and does not contact the backend", async () => {
    const response = await GET(makeRequest({ csrfHeader: null }));

    expect(response.status).toBe(403);
    expect(mockBackendFetch).not.toHaveBeenCalled();
  });

  it("forwards the invalid-window code and its detail so the page can show it", async () => {
    mockBackendFetch.mockResolvedValue({
      ok: false,
      status: 400,
      json: async () => ({
        title: "Invalid documentation analysis window",
        detail: "The window must not exceed 92 days (about one quarter)",
        code: "DOCUMENTATION_ANALYSIS_WINDOW_INVALID",
      }),
    } as unknown as Response);

    const response = await GET(makeRequest({ query: "?from=2025-01-01&to=2025-04-03" }));

    expect(response.status).toBe(400);
    await expect(response.json()).resolves.toEqual({
      error: "Backend error: 400",
      code: "DOCUMENTATION_ANALYSIS_WINDOW_INVALID",
      detail: "The window must not exceed 92 days (about one quarter)",
    });
  });

  it("returns 401 when there is no session token", async () => {
    mockBackendFetch.mockResolvedValue(null);

    const response = await GET(makeRequest());

    expect(response.status).toBe(401);
  });
});
