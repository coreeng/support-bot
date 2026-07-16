import type { NextRequest } from "next/server";
import { backendFetch } from "../../../_lib/backend-fetch";
import { PAGE_PARAMS, proxyElevateGet } from "../proxy-elevate-get";

jest.mock("../../../_lib/backend-fetch", () => ({
  backendFetch: jest.fn(),
  unauthorizedResponse: jest.fn(() => Response.json({ error: "Unauthorized" }, { status: 401 })),
}));

const mockBackendFetch = backendFetch as jest.MockedFunction<typeof backendFetch>;

class TestResponse {
  body: string | null;
  status: number;
  ok: boolean;
  headers: Headers;

  constructor(body: string | null, init: { status?: number; headers?: Record<string, string> } = {}) {
    this.body = body;
    this.status = init.status ?? 200;
    this.ok = this.status >= 200 && this.status < 300;
    this.headers = new Headers();
    for (const [key, value] of Object.entries(init.headers ?? {})) this.headers.set(key, value);
  }

  static json(value: unknown, init: { status?: number } = {}) {
    return new TestResponse(JSON.stringify(value), { status: init.status, headers: { "content-type": "application/json" } });
  }

  async json() {
    return JSON.parse(this.body ?? "null");
  }
}

global.Response = TestResponse as unknown as typeof Response;

function request(url: string, signal = new AbortController().signal) {
  return { nextUrl: new URL(url), signal } as NextRequest;
}

describe("proxyElevateGet", () => {
  beforeEach(() => jest.clearAllMocks());

  it("forwards only the explicitly supported query parameters", async () => {
    mockBackendFetch.mockResolvedValue(Response.json({ content: [] }));
    const nextRequest = request(
      "http://localhost/api/elevate/products?snapshotVersion=snapshot-1&page=2&pageSize=50&query=runtime&relationship=linked&sort=relationships&direction=desc&admin=true"
    );

    await proxyElevateGet(nextRequest, "/elevate/products", PAGE_PARAMS);

    expect(mockBackendFetch).toHaveBeenCalledWith(
      nextRequest,
      "/elevate/products?snapshotVersion=snapshot-1&page=2&pageSize=50&query=runtime&relationship=linked&sort=relationships&direction=desc",
      { signal: nextRequest.signal }
    );
  });

  it("forwards the request abort signal to the backend fetch", async () => {
    mockBackendFetch.mockResolvedValue(Response.json({ ok: true }));
    const controller = new AbortController();
    const nextRequest = request("http://localhost/api/elevate/status", controller.signal);

    await proxyElevateGet(nextRequest, "/elevate/status");

    expect(mockBackendFetch).toHaveBeenCalledWith(nextRequest, "/elevate/status", { signal: controller.signal });
  });

  it.each([403, 409])("preserves backend %s responses", async (status) => {
    mockBackendFetch.mockResolvedValue(Response.json({ reason: "SNAPSHOT_CHANGED" }, { status }));
    const nextRequest = request("http://localhost/api/elevate/products?snapshotVersion=snapshot-1");

    const response = await proxyElevateGet(nextRequest, "/elevate/products", PAGE_PARAMS);

    expect(response.status).toBe(status);
    await expect(response.json()).resolves.toEqual({ reason: "SNAPSHOT_CHANGED" });
  });

  it("returns 401 when there is no authenticated backend token", async () => {
    mockBackendFetch.mockResolvedValue(null);

    const response = await proxyElevateGet(request("http://localhost/api/elevate/status"), "/elevate/status");

    expect(response.status).toBe(401);
  });
});
