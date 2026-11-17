import type { NextRequest } from "next/server";
import { backendFetch } from "../backend-fetch";
import { redirectToBackendPermalink } from "../permalink-redirect";

jest.mock("../backend-fetch", () => ({
  backendFetch: jest.fn(),
  unauthorizedResponse: jest.fn(() => ({ kind: "unauthorized", status: 401 })),
}));

jest.mock("next/server", () => ({
  NextResponse: { redirect: jest.fn((url: string) => ({ kind: "redirect", url, status: 307 })) },
}));

const mockBackendFetch = backendFetch as jest.MockedFunction<typeof backendFetch>;

// jsdom has no fetch Response; a minimal stand-in covering what the helper reads and builds.
class TestResponse {
  status: number;
  ok: boolean;
  headers: Record<string, string>;
  private readonly payload: unknown;

  constructor(payload: unknown, init: { status?: number; headers?: Record<string, string> } = {}) {
    this.payload = payload;
    this.status = init.status ?? 200;
    this.ok = this.status >= 200 && this.status < 300;
    this.headers = init.headers ?? {};
  }

  async json() {
    return this.payload;
  }
}

global.Response = TestResponse as unknown as typeof Response;

const request = {} as NextRequest;

describe("redirectToBackendPermalink", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    jest.spyOn(console, "error").mockImplementation();
  });

  afterEach(() => jest.restoreAllMocks());

  it("redirects to the permalink the backend returns", async () => {
    mockBackendFetch.mockResolvedValue(new TestResponse({ permalink: "https://example.com/archives/C123/p1" }) as unknown as Response);

    const result = (await redirectToBackendPermalink(request, "/ticket/7/permalink", "ticket 7")) as unknown as {
      kind: string;
      url: string;
    };

    expect(mockBackendFetch).toHaveBeenCalledWith(request, "/ticket/7/permalink");
    expect(result).toEqual(expect.objectContaining({ kind: "redirect", url: "https://example.com/archives/C123/p1" }));
  });

  it("returns unauthorized when there is no backend session", async () => {
    mockBackendFetch.mockResolvedValue(null);

    const result = (await redirectToBackendPermalink(request, "/ticket/7/permalink", "ticket 7")) as unknown as { status: number };

    expect(result.status).toBe(401);
  });

  it("renders an error page with the backend status on failure", async () => {
    mockBackendFetch.mockResolvedValue(new TestResponse(null, { status: 404 }) as unknown as Response);

    const result = (await redirectToBackendPermalink(request, "/ticket/7/permalink", "ticket 7")) as unknown as TestResponse;

    expect(result.status).toBe(404);
    expect(result.headers).toEqual({ "Content-Type": "text/html" });
  });

  it("refuses non-https permalinks", async () => {
    mockBackendFetch.mockResolvedValue(new TestResponse({ permalink: "javascript:alert(1)" }) as unknown as Response);

    const result = (await redirectToBackendPermalink(request, "/ticket/7/permalink", "ticket 7")) as unknown as TestResponse;

    expect(result.status).toBe(502);
  });
});
