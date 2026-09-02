import { backendFetch } from "../_lib/backend-fetch";
import { GET } from "./route";

// backend-error is deliberately left unmocked so this test pins the exact body shape
// useSummary parses, including the forwarded backend `code`.
jest.mock("../_lib/backend-fetch", () => ({
  backendFetch: jest.fn(),
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

function makeRequest(query = ""): Request {
  return { url: `http://localhost/api/summary${query}` } as unknown as Request;
}

describe("summary BFF route", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("forwards the window to the backend summary endpoint and returns its payload", async () => {
    mockBackendFetch.mockResolvedValue({
      ok: true,
      json: async () => ({ summary: "text" }),
    } as Response);
    const request = makeRequest("?from=2026-01-01&to=2026-01-31");

    const response = await GET(request);

    expect(mockBackendFetch).toHaveBeenCalledWith(request, "/summary?from=2026-01-01&to=2026-01-31", {}, "/summary");
    await expect(response.json()).resolves.toEqual({ summary: "text" });
  });

  it("forwards the backend error code so the page can distinguish failures", async () => {
    mockBackendFetch.mockResolvedValue({
      ok: false,
      status: 400,
      json: async () => ({ detail: "The requested window is invalid", code: "SUMMARY_WINDOW_INVALID" }),
    } as unknown as Response);

    const response = await GET(makeRequest("?from=2026-01-31&to=2026-01-01"));

    expect(response.status).toBe(400);
    await expect(response.json()).resolves.toEqual({
      error: "Backend error: 400",
      code: "SUMMARY_WINDOW_INVALID",
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
