import type { NextRequest } from "next/server";
import { backendFetch } from "../../_lib/backend-fetch";
import { GET } from "./route";

jest.mock("../../_lib/backend-fetch", () => ({
  backendFetch: jest.fn(),
  errorResponse: (message: string, status = 500) => ({ status, json: async () => ({ error: message }) }),
  unauthorizedResponse: () => ({ status: 401, json: async () => ({ error: "Unauthorized" }) }),
}));

const mockBackendFetch = backendFetch as jest.MockedFunction<typeof backendFetch>;

// jsdom has no global Response; provide the static json() the route uses, honouring init.status.
const originalResponse = global.Response;
beforeAll(() => {
  global.Response = {
    json: (data: unknown, init?: { status?: number }) => ({ status: init?.status ?? 200, json: async () => data }),
  } as unknown as typeof Response;
});
afterAll(() => {
  global.Response = originalResponse;
});

const request = {} as NextRequest;

describe("summary team groups BFF route", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("forwards to the backend and returns the groups as they are", async () => {
    const groups = {
      displayName: "Area",
      groups: [{ code: "area-a", label: "Area A", teams: [{ code: "team-a", label: "Team A" }] }],
    };
    mockBackendFetch.mockResolvedValue({ ok: true, json: async () => groups } as Response);

    const response = await GET(request);

    expect(mockBackendFetch).toHaveBeenCalledWith(request, "/summary/team-groups");
    await expect(response.json()).resolves.toEqual(groups);
  });

  it("forwards a backend error with its status", async () => {
    mockBackendFetch.mockResolvedValue({
      ok: false,
      status: 403,
      json: async () => ({ error: "Forbidden" }),
    } as unknown as Response);

    const response = await GET(request);

    expect(response.status).toBe(403);
  });

  it("returns 401 when there is no session token", async () => {
    mockBackendFetch.mockResolvedValue(null);

    const response = await GET(request);

    expect(response.status).toBe(401);
  });
});
