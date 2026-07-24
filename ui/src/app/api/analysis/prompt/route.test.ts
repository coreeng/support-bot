import type { NextRequest } from "next/server";
import { backendFetch } from "../../_lib/backend-fetch";
import { GET } from "./route";

jest.mock("../../_lib/backend-fetch", () => ({
  backendFetch: jest.fn(),
  errorResponse: (message: string, status = 500) => ({ status, json: async () => ({ error: message }) }),
  unauthorizedResponse: () => ({ status: 401, json: async () => ({ error: "Unauthorized" }) }),
}));

const mockBackendFetch = backendFetch as jest.MockedFunction<typeof backendFetch>;

// jsdom has no global Response; provide the static json() the route uses for its success path.
const originalResponse = global.Response;
beforeAll(() => {
  global.Response = {
    json: (data: unknown) => ({ status: 200, json: async () => data }),
  } as unknown as typeof Response;
});
afterAll(() => {
  global.Response = originalResponse;
});

describe("analysis prompt BFF route", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("forwards to the backend prompt endpoint and returns its payload", async () => {
    mockBackendFetch.mockResolvedValue({
      ok: true,
      json: async () => ({ prompt: "prompt text" }),
    } as Response);
    const request = {} as NextRequest;

    const response = await GET(request);

    expect(mockBackendFetch).toHaveBeenCalledWith(request, "/analysis/prompt");
    await expect(response.json()).resolves.toEqual({ prompt: "prompt text" });
  });

  it("forwards backend errors without a fallback", async () => {
    mockBackendFetch.mockResolvedValue({ ok: false, status: 500 } as Response);

    const response = await GET({} as NextRequest);

    expect(response.status).toBe(500);
    await expect(response.json()).resolves.toEqual({ error: "Backend error: 500" });
  });

  it("returns 401 when there is no session token", async () => {
    mockBackendFetch.mockResolvedValue(null);

    const response = await GET({} as NextRequest);

    expect(response.status).toBe(401);
  });
});
