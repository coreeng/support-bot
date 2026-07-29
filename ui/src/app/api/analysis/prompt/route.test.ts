import type { NextRequest } from "next/server";
import { backendFetch } from "../../_lib/backend-fetch";
import { GET } from "./route";

// backend-error is deliberately left unmocked so this test pins the exact body shape
// useAnalysisPrompt parses, including the forwarded backend `code`.
jest.mock("../../_lib/backend-fetch", () => ({
  backendFetch: jest.fn(),
  errorResponse: (message: string, status = 500) => ({ status, json: async () => ({ error: message }) }),
  unauthorizedResponse: () => ({ status: 401, json: async () => ({ error: "Unauthorized" }) }),
}));

const mockBackendFetch = backendFetch as jest.MockedFunction<typeof backendFetch>;

// jsdom has no global Response; provide the static json() the route uses, honouring init.status
// so the helpers' status codes are asserted rather than defaulted away.
const originalResponse = global.Response;
beforeAll(() => {
  global.Response = {
    json: (data: unknown, init?: { status?: number }) => ({ status: init?.status ?? 200, json: async () => data }),
  } as unknown as typeof Response;
});
afterAll(() => {
  global.Response = originalResponse;
});

function makeRequest({
  csrfHeader = "csrf-token",
  csrfCookie = "csrf-token|hash",
}: { csrfHeader?: string | null; csrfCookie?: string | null } = {}): NextRequest {
  return {
    headers: { get: (name: string) => (name === "X-CSRF-Token" ? csrfHeader : null) },
    cookies: {
      get: (name: string) => (name === "authjs.csrf-token" && csrfCookie !== null ? { value: csrfCookie } : undefined),
    },
  } as unknown as NextRequest;
}

describe("analysis prompt BFF route", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("forwards to the backend prompt endpoint and returns its payload", async () => {
    mockBackendFetch.mockResolvedValue({
      ok: true,
      json: async () => ({ prompt: "prompt text" }),
    } as Response);
    const request = makeRequest();

    const response = await GET(request);

    expect(mockBackendFetch).toHaveBeenCalledWith(request, "/analysis/prompt");
    await expect(response.json()).resolves.toEqual({ prompt: "prompt text" });
  });

  it("returns 403 without contacting the backend when the CSRF header is missing", async () => {
    const response = await GET(makeRequest({ csrfHeader: null }));

    expect(response.status).toBe(403);
    await expect(response.json()).resolves.toEqual({ error: "Missing CSRF token" });
    expect(mockBackendFetch).not.toHaveBeenCalled();
  });

  it("returns 403 without contacting the backend when the CSRF cookie is missing", async () => {
    const response = await GET(makeRequest({ csrfCookie: null }));

    expect(response.status).toBe(403);
    await expect(response.json()).resolves.toEqual({ error: "Missing CSRF token" });
    expect(mockBackendFetch).not.toHaveBeenCalled();
  });

  it("returns 403 without contacting the backend when the CSRF header does not match the cookie", async () => {
    const response = await GET(makeRequest({ csrfHeader: "other-token" }));

    expect(response.status).toBe(403);
    await expect(response.json()).resolves.toEqual({ error: "Invalid CSRF token" });
    expect(mockBackendFetch).not.toHaveBeenCalled();
  });

  it("forwards the backend error code so the dialog can distinguish failures", async () => {
    mockBackendFetch.mockResolvedValue({
      ok: false,
      status: 500,
      json: async () => ({ code: "ANALYSIS_PROMPT_LOAD_FAILED" }),
    } as unknown as Response);

    const response = await GET(makeRequest());

    expect(response.status).toBe(500);
    await expect(response.json()).resolves.toEqual({
      error: "Backend error: 500",
      code: "ANALYSIS_PROMPT_LOAD_FAILED",
    });
  });

  it("forwards backend errors without a fallback when the body carries no code", async () => {
    mockBackendFetch.mockResolvedValue({
      ok: false,
      status: 502,
      json: async () => {
        throw new SyntaxError("not JSON");
      },
    } as unknown as Response);

    const response = await GET(makeRequest());

    expect(response.status).toBe(502);
    await expect(response.json()).resolves.toEqual({ error: "Backend error: 502" });
  });

  it("returns 401 when there is no session token", async () => {
    mockBackendFetch.mockResolvedValue(null);

    const response = await GET(makeRequest());

    expect(response.status).toBe(401);
  });
});
