import type { NextRequest } from "next/server";
import { backendFetch } from "../../_lib/backend-fetch";
import { GET } from "./route";

jest.mock("../../_lib/backend-fetch", () => ({
  backendFetch: jest.fn(),
  errorResponse: (message: string, status = 500) => ({ status, json: async () => ({ error: message }) }),
  unauthorizedResponse: () => ({ status: 401, json: async () => ({ error: "Unauthorized" }) }),
}));

const mockBackendFetch = backendFetch as jest.MockedFunction<typeof backendFetch>;

describe("dashboard BFF route", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("passes the authenticated request to the backend without making a UI role decision", async () => {
    mockBackendFetch.mockResolvedValue({ ok: false, status: 403 } as Response);
    const request = {
      nextUrl: new URL("http://localhost/api/dashboard/unattended-queries-count"),
    } as unknown as NextRequest;

    const response = await GET(request, { params: Promise.resolve({ endpoint: "unattended-queries-count" }) });

    expect(mockBackendFetch).toHaveBeenCalledWith(request, "/dashboard/unattended-queries-count");
    expect(response.status).toBe(403);
    await expect(response.json()).resolves.toEqual({ error: "Backend error: 403" });
  });

  it("returns 401 when the authenticated backend fetch has no session token", async () => {
    mockBackendFetch.mockResolvedValue(null);
    const request = {
      nextUrl: new URL("http://localhost/api/dashboard/unattended-queries-count"),
    } as unknown as NextRequest;

    const response = await GET(request, { params: Promise.resolve({ endpoint: "unattended-queries-count" }) });

    expect(response.status).toBe(401);
  });
});
