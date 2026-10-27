import type { NextRequest } from "next/server";
import { backendFetch } from "../../../_lib/backend-fetch";
import { GET } from "../route";

jest.mock("../../../_lib/backend-fetch", () => ({
  backendFetch: jest.fn(),
  unauthorizedResponse: jest.fn(() => Response.json({ error: "Unauthorized" }, { status: 401 })),
  errorResponse: jest.fn((message: string, status: number) => Response.json({ error: message }, { status })),
}));

const mockBackendFetch = backendFetch as jest.MockedFunction<typeof backendFetch>;

// jsdom has no fetch Response; same stand-in as the elevate proxy tests
class TestResponse {
  body: string | null;
  status: number;
  ok: boolean;

  constructor(body: string | null, init: { status?: number } = {}) {
    this.body = body;
    this.status = init.status ?? 200;
    this.ok = this.status >= 200 && this.status < 300;
  }

  static json(value: unknown, init: { status?: number } = {}) {
    return new TestResponse(JSON.stringify(value), init);
  }

  async json() {
    return JSON.parse(this.body ?? "null");
  }
}

global.Response = TestResponse as unknown as typeof Response;

function request(url: string) {
  return { nextUrl: new URL(url) } as NextRequest;
}

describe("GET /api/rating/feedback", () => {
  beforeEach(() => jest.clearAllMocks());

  it("maps the date range onto the backend's parameters", async () => {
    const items = [{ rating: 5, weekStart: "2025-10-06", feedback: "Great", ticketId: 9 }];
    mockBackendFetch.mockResolvedValue(Response.json(items));

    const res = await GET(request("http://localhost/api/rating/feedback?from=2025-10-01&to=2025-10-31"));

    expect(mockBackendFetch).toHaveBeenCalledWith(expect.anything(), "/rating/feedback?dateFrom=2025-10-01&dateTo=2025-10-31");
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual(items);
  });

  it("omits an empty range", async () => {
    mockBackendFetch.mockResolvedValue(Response.json([]));

    await GET(request("http://localhost/api/rating/feedback"));

    expect(mockBackendFetch).toHaveBeenCalledWith(expect.anything(), "/rating/feedback");
  });

  it("passes a 403 through", async () => {
    mockBackendFetch.mockResolvedValue(new TestResponse(null, { status: 403 }) as unknown as Response);

    const res = await GET(request("http://localhost/api/rating/feedback"));

    expect(res.status).toBe(403);
  });

  it("returns 401 without a session", async () => {
    mockBackendFetch.mockResolvedValue(null);

    const res = await GET(request("http://localhost/api/rating/feedback"));

    expect(res.status).toBe(401);
  });
});
